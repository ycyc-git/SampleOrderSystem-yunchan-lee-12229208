package org.example.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.util.GsonConfig;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class OrderRepository {

    private static final String DEFAULT_PATH = "data/orders.json";

    private final String filePath;
    private final Gson gson;
    private final SampleRepository sampleRepo;
    private final Map<String, Order> store = new LinkedHashMap<>();

    public OrderRepository(SampleRepository sampleRepo) {
        this(DEFAULT_PATH, sampleRepo);
    }

    public OrderRepository(String filePath, SampleRepository sampleRepo) {
        this.filePath = filePath;
        this.gson = GsonConfig.create();
        this.sampleRepo = sampleRepo;
        loadFromFile();
    }

    public void save(Order order) {
        store.put(order.getOrderId(), order);
        saveToFile();
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }

    public List<Order> findByStatus(OrderStatus status) {
        return store.values().stream()
                .filter(o -> o.getStatus() == status)
                .collect(Collectors.toList());
    }

    private void loadFromFile() {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<OrderDto>>() {}.getType();
            List<OrderDto> dtos = gson.fromJson(reader, listType);
            if (dtos != null) {
                for (OrderDto dto : dtos) {
                    Sample sample = sampleRepo.findById(dto.sampleId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "참조 시료 없음: " + dto.sampleId));
                    Order order = dto.toOrder(sample);
                    store.put(order.getOrderId(), order);
                }
            }
        } catch (IOException e) {
            // start empty on read error
        }
    }

    private void saveToFile() {
        Path path = Paths.get(filePath);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            List<OrderDto> dtos = store.values().stream()
                    .map(OrderDto::fromOrder)
                    .collect(Collectors.toList());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(dtos, writer);
            }
        } catch (IOException e) {
            // ignore write errors
        }
    }

    // ── JSON DTO ──────────────────────────────────────────────────

    private static class OrderDto {
        String orderId;
        String sampleId;
        String customerName;
        int quantity;
        String status;
        String createdAt;
        String releasedAt;

        static OrderDto fromOrder(Order o) {
            OrderDto dto = new OrderDto();
            dto.orderId = o.getOrderId();
            dto.sampleId = o.getSample().getId();
            dto.customerName = o.getCustomerName();
            dto.quantity = o.getQuantity();
            dto.status = o.getStatus().name();
            dto.createdAt = o.getCreatedAt().toString();
            dto.releasedAt = o.getReleasedAt() != null ? o.getReleasedAt().toString() : null;
            return dto;
        }

        Order toOrder(Sample sample) {
            return new Order(
                    orderId, sample, customerName, quantity,
                    OrderStatus.valueOf(status),
                    LocalDateTime.parse(createdAt),
                    releasedAt != null ? LocalDateTime.parse(releasedAt) : null);
        }
    }
}
