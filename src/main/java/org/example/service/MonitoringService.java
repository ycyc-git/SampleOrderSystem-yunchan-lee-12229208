package org.example.service;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.domain.StockStatusDto;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MonitoringService {

    private final OrderRepository orderRepository;
    private final SampleRepository sampleRepository;

    public MonitoringService(OrderRepository orderRepository,
                             SampleRepository sampleRepository) {
        this.orderRepository = orderRepository;
        this.sampleRepository = sampleRepository;
    }

    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public Map<OrderStatus, Long> getOrderSummaryByStatus() {
        Map<OrderStatus, Long> result = new EnumMap<>(OrderStatus.class);
        orderRepository.findAll().stream()
                .filter(o -> o.getStatus() != OrderStatus.REJECTED)
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()))
                .forEach(result::put);
        return result;
    }

    public List<StockStatusDto> getStockStatus() {
        // pendingDemand: 아직 승인되지 않은 RESERVED 주문 수량 (가용 재고와 경쟁)
        List<Order> reservedOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.RESERVED)
                .collect(Collectors.toList());

        return sampleRepository.findAll().stream()
                .map(sample -> buildDto(sample, reservedOrders))
                .collect(Collectors.toList());
    }

    private StockStatusDto buildDto(Sample sample, List<Order> reservedOrders) {
        int stock = sample.getStock();
        int reservedStock = sample.getReservedStock();
        int pendingDemand = reservedOrders.stream()
                .filter(o -> o.getSample().getId().equals(sample.getId()))
                .mapToInt(Order::getQuantity)
                .sum();
        String stockLabel = stock == 0 ? "고갈"
                : stock < pendingDemand ? "부족" : "여유";
        int remainingRate = (stock + pendingDemand == 0) ? 0
                : (int) (stock * 100.0 / (stock + pendingDemand));
        return new StockStatusDto(sample, stock, reservedStock, pendingDemand, stockLabel, remainingRate);
    }
}
