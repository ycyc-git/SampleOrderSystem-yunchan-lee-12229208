package org.example.service;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;

import java.time.LocalDateTime;
import java.util.List;

public class ReleaseService {

    private final OrderRepository orderRepository;
    private final SampleRepository sampleRepository;
    private final ProductionLineService productionLineService;

    public ReleaseService(OrderRepository orderRepository,
                          SampleRepository sampleRepository) {
        this(orderRepository, sampleRepository, null);
    }

    public ReleaseService(OrderRepository orderRepository,
                          SampleRepository sampleRepository,
                          ProductionLineService productionLineService) {
        this.orderRepository = orderRepository;
        this.sampleRepository = sampleRepository;
        this.productionLineService = productionLineService;
    }

    public List<Order> getConfirmedOrders() {
        return orderRepository.findByStatus(OrderStatus.CONFIRMED);
    }

    public Order release(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 주문입니다."));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "CONFIRMED 상태의 주문만 출고할 수 있습니다. 현재 상태: "
                    + order.getStatus());
        }

        Sample sample = order.getSample();
        if (sample.getStock() < order.getQuantity()) {
            throw new IllegalStateException(
                    "재고가 부족합니다. 현재 재고: " + sample.getStock()
                    + " ea, 주문 수량: " + order.getQuantity() + " ea");
        }

        sample.setStock(sample.getStock() - order.getQuantity());
        sampleRepository.save(sample);

        order.transition(OrderStatus.RELEASE);
        order.setReleasedAt(LocalDateTime.now());
        orderRepository.save(order);

        return order;
    }

    public Order requeueToProducing(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 주문입니다."));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "CONFIRMED 상태의 주문만 재생산 큐에 등록할 수 있습니다. 현재 상태: "
                    + order.getStatus());
        }

        Sample sample = order.getSample();
        int shortage = order.getQuantity() - sample.getStock();
        if (shortage <= 0) {
            throw new IllegalStateException(
                    "재고가 충분합니다. 재생산이 필요하지 않습니다.");
        }

        order.transition(OrderStatus.PRODUCING);
        orderRepository.save(order);

        if (productionLineService != null) {
            productionLineService.enqueue(order, shortage);
        }

        return order;
    }
}
