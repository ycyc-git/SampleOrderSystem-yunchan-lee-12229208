package org.example.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.domain.Order;
import org.example.domain.ProductionJob;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.util.GsonConfig;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ProductionLineService {

    private static final String DEFAULT_PATH = "data/production_jobs.json";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String filePath;
    private final OrderRepository orderRepo;
    private final Gson gson;
    private final LinkedList<ProductionJob> queue = new LinkedList<>();

    public ProductionLineService(OrderRepository orderRepo) {
        this(DEFAULT_PATH, orderRepo);
    }

    public ProductionLineService(String filePath, OrderRepository orderRepo) {
        this.filePath = filePath;
        this.orderRepo = orderRepo;
        this.gson = GsonConfig.create();
        loadFromFile();
    }

    public ProductionJob enqueue(Order order, int shortage) {
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

    public Optional<ProductionJob> getCurrentJob() {
        return queue.isEmpty() ? Optional.empty() : Optional.of(queue.peek());
    }

    public List<ProductionJob> getWaitingQueue() {
        if (queue.size() <= 1) return List.of();
        List<ProductionJob> all = new ArrayList<>(queue);
        return all.subList(1, all.size());
    }

    public int getTotalQueueSize() {
        return queue.size();
    }

    public int getProgressPercent() {
        return 0; // Phase 08에서 tick()과 함께 구현
    }

    private String generateJobId() {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String prefix = "PJ-" + dateStr + "-";
        long count = queue.stream()
                .filter(j -> j.getJobId().startsWith(prefix))
                .count();
        return String.format("%s%04d", prefix, count + 1);
    }

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
