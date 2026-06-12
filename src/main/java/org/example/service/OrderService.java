package org.example.service;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class OrderService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository repository;
    private final SampleRepository sampleRepository;
    private final ProductionLineService productionLineService;

    public OrderService(OrderRepository repository, SampleRepository sampleRepository) {
        this(repository, sampleRepository, null);
    }

    public OrderService(OrderRepository repository, SampleRepository sampleRepository,
                        ProductionLineService productionLineService) {
        this.repository = repository;
        this.sampleRepository = sampleRepository;
        this.productionLineService = productionLineService;
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

    public List<Order> getReservedOrders() {
        return repository.findByStatus(OrderStatus.RESERVED);
    }

    public StockAnalysisResult analyzeStock(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다."));
        Sample sample = order.getSample();
        int shortage = Math.max(0, order.getQuantity() - sample.getStock());
        int actualQty = 0;
        double totalTime = 0;
        if (shortage > 0) {
            actualQty = (int) Math.ceil(shortage / (sample.getYield() * 0.9));
            totalTime = sample.getAvgProductionTime() * actualQty;
        }
        return new StockAnalysisResult(sample.getStock(), order.getQuantity(),
                shortage, actualQty, totalTime);
    }

    public Order approve(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다."));
        Sample sample = order.getSample();
        int shortage = Math.max(0, order.getQuantity() - sample.getStock());
        if (shortage == 0) {
            sample.setStock(sample.getStock() - order.getQuantity());
            sampleRepository.save(sample);
            order.transition(OrderStatus.CONFIRMED);
        } else {
            order.transition(OrderStatus.PRODUCING);
            if (productionLineService != null) {
                productionLineService.enqueue(order, shortage);
            }
        }
        repository.save(order);
        return order;
    }

    public Order reject(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다."));
        order.transition(OrderStatus.REJECTED);
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

    public static class StockAnalysisResult {
        public final int currentStock;
        public final int quantity;
        public final int shortage;
        public final int actualQty;
        public final double totalTime;

        StockAnalysisResult(int currentStock, int quantity, int shortage,
                            int actualQty, double totalTime) {
            this.currentStock = currentStock;
            this.quantity = quantity;
            this.shortage = shortage;
            this.actualQty = actualQty;
            this.totalTime = totalTime;
        }
    }
}
