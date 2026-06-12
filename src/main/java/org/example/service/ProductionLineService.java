package org.example.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.ProductionJob;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.example.util.GsonConfig;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ProductionLineService {

    private static final String DEFAULT_PATH = "data/production_jobs.json";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** 시연 모드: 1분 = 1초 (1000ms). 실 운영 시 60_000L 으로 교체. */
    static final long DEFAULT_MS_PER_MINUTE = 1_000L;

    private final String filePath;
    private final OrderRepository orderRepo;
    private final SampleRepository sampleRepo;
    private final Gson gson;
    private final long msPerMinute;
    private final LinkedList<ProductionJob> queue = new LinkedList<>();

    public ProductionLineService(OrderRepository orderRepo, SampleRepository sampleRepo) {
        this(DEFAULT_PATH, orderRepo, sampleRepo, DEFAULT_MS_PER_MINUTE);
    }

    public ProductionLineService(String filePath, OrderRepository orderRepo,
                                 SampleRepository sampleRepo) {
        this(filePath, orderRepo, sampleRepo, DEFAULT_MS_PER_MINUTE);
    }

    ProductionLineService(String filePath, OrderRepository orderRepo,
                          SampleRepository sampleRepo, long msPerMinute) {
        this.filePath = filePath;
        this.orderRepo = orderRepo;
        this.sampleRepo = sampleRepo;
        this.msPerMinute = msPerMinute;
        this.gson = GsonConfig.create();
        loadFromFile();
    }

    // ── 생산 큐 ───────────────────────────────────────────────────

    public synchronized ProductionJob enqueue(Order order, int shortage) {
        Sample sample = order.getSample();
        int actualQty = (int) Math.ceil(shortage / (sample.getYield() * 0.9));
        double totalTime = sample.getAvgProductionTime() * actualQty;
        String jobId = generateJobId();
        LocalDateTime startedAt = queue.isEmpty() ? LocalDateTime.now() : null;
        ProductionJob job = new ProductionJob(jobId, order, shortage,
                actualQty, totalTime, startedAt);
        queue.add(job);
        saveToFile();
        return job;
    }

    public synchronized Optional<ProductionJob> getCurrentJob() {
        return queue.isEmpty() ? Optional.empty() : Optional.of(queue.peek());
    }

    public synchronized List<ProductionJob> getWaitingQueue() {
        if (queue.size() <= 1) return List.of();
        List<ProductionJob> all = new ArrayList<>(queue);
        return all.subList(1, all.size());
    }

    public synchronized int getTotalQueueSize() {
        return queue.size();
    }

    // ── tick ──────────────────────────────────────────────────────

    public synchronized void tick() {
        ProductionJob current = queue.peek();
        if (current == null) return; // IDLE

        if (current.getStartedAt() == null) {
            current.setStartedAt(LocalDateTime.now());
        }

        long elapsed = System.currentTimeMillis() - toEpochMillis(current.getStartedAt());
        long totalMs = (long) (current.getTotalProductionTime() * msPerMinute);

        if (elapsed < totalMs) return; // 진행 중

        // ── 생산 완료 처리 ────────────────────────────────────────
        Sample sample = current.getOrder().getSample();
        int shortage = current.getShortage();
        int actualQty = current.getActualProductionQty();
        // 생산량 중 부족분은 예약 재고로, 초과분은 가용 재고로
        sample.setStock(sample.getStock() + actualQty - shortage);
        sample.setReservedStock(sample.getReservedStock() + shortage);
        sampleRepo.save(sample);

        current.getOrder().transition(OrderStatus.CONFIRMED);
        orderRepo.save(current.getOrder());

        queue.poll();

        ProductionJob next = queue.peek();
        if (next != null) {
            next.setStartedAt(LocalDateTime.now());
        }
        saveToFile();
    }

    // ── 진행률 / 완료 예정 ────────────────────────────────────────

    public synchronized int getProgressPercent() {
        ProductionJob current = queue.peek();
        if (current == null || current.getStartedAt() == null) return 0;
        long elapsed = System.currentTimeMillis() - toEpochMillis(current.getStartedAt());
        long totalMs = (long) (current.getTotalProductionTime() * msPerMinute);
        return (int) Math.min(100, elapsed * 100 / totalMs);
    }

    public synchronized String getEstimatedFinishTime() {
        ProductionJob current = queue.peek();
        if (current == null || current.getStartedAt() == null) return "--:--";
        long elapsed = System.currentTimeMillis() - toEpochMillis(current.getStartedAt());
        long totalMs = (long) (current.getTotalProductionTime() * msPerMinute);
        long remainMs = Math.max(0, totalMs - elapsed);
        return LocalTime.now().plusSeconds(remainMs / 1000).format(TIME_FMT);
    }

    public synchronized long getMsPerMinute() {
        return msPerMinute;
    }

    public synchronized long getRemainingMs() {
        ProductionJob current = queue.peek();
        if (current == null || current.getStartedAt() == null) return 0;
        long elapsed = System.currentTimeMillis() - toEpochMillis(current.getStartedAt());
        long totalMs = (long) (current.getTotalProductionTime() * msPerMinute);
        return Math.max(0, totalMs - elapsed);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────

    private long toEpochMillis(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String generateJobId() {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String prefix = "PJ-" + dateStr + "-";
        long count = queue.stream()
                .filter(j -> j.getJobId().startsWith(prefix))
                .count();
        return String.format("%s%04d", prefix, count + 1);
    }

    // ── 영속성 ────────────────────────────────────────────────────

    private void loadFromFile() {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<ProductionJobDto>>() {}.getType();
            List<ProductionJobDto> dtos = gson.fromJson(reader, listType);
            if (dtos == null) return;
            for (ProductionJobDto dto : dtos) {
                Order order = orderRepo.findById(dto.orderId)
                        .orElseThrow(() -> new IllegalStateException(
                                "참조 주문 없음: " + dto.orderId));
                queue.add(dto.toJob(order));
            }
        } catch (IOException e) {
            // start empty on read error
        }
    }

    private void saveToFile() {
        Path path = Paths.get(filePath);
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            List<ProductionJobDto> dtos = new ArrayList<>();
            for (ProductionJob job : queue) dtos.add(ProductionJobDto.fromJob(job));
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(dtos, writer);
            }
        } catch (IOException e) {
            // ignore write errors
        }
    }

    private static class ProductionJobDto {
        String jobId;
        String orderId;
        int shortage;
        int actualProductionQty;
        double totalProductionTime;
        String startedAt;

        static ProductionJobDto fromJob(ProductionJob job) {
            ProductionJobDto dto = new ProductionJobDto();
            dto.jobId = job.getJobId();
            dto.orderId = job.getOrder().getOrderId();
            dto.shortage = job.getShortage();
            dto.actualProductionQty = job.getActualProductionQty();
            dto.totalProductionTime = job.getTotalProductionTime();
            dto.startedAt = job.getStartedAt() == null ? null : job.getStartedAt().toString();
            return dto;
        }

        ProductionJob toJob(Order order) {
            LocalDateTime started = startedAt == null ? null : LocalDateTime.parse(startedAt);
            return new ProductionJob(jobId, order, shortage,
                    actualProductionQty, totalProductionTime, started);
        }
    }
}
