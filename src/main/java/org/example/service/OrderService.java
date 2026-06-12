package org.example.service;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class OrderService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository repository;
    private final SampleRepository sampleRepository;

    public OrderService(OrderRepository repository, SampleRepository sampleRepository) {
        this.repository = repository;
        this.sampleRepository = sampleRepository;
    }

    public Order reserve(String sampleId, String customerName, int quantity) {
        Sample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 시료 ID입니다."));
        if (customerName == null || customerName.isBlank())
            throw new IllegalArgumentException("고객명을 입력하세요.");
        if (quantity <= 0)
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다.");

        String orderId = generateOrderId();
        Order order = new Order(orderId, sample, customerName, quantity,
                OrderStatus.RESERVED, LocalDateTime.now(), null);
        repository.save(order);
        return order;
    }

    public Optional<Sample> findSampleById(String sampleId) {
        return sampleRepository.findById(sampleId);
    }

    public int getTotalOrders() {
        return (int) repository.findAll().stream()
                .filter(o -> o.getStatus() != OrderStatus.REJECTED)
                .count();
    }

    private String generateOrderId() {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String prefix = "ORD-" + dateStr + "-";
        long count = repository.findAll().stream()
                .filter(o -> o.getOrderId().startsWith(prefix))
                .count();
        return String.format("%s%04d", prefix, count + 1);
    }
}
