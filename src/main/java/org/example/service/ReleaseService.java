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
        if (sample.getReservedStock() < order.getQuantity()) {
            throw new IllegalStateException(
                    "예약 재고가 부족합니다. 예약 재고: " + sample.getReservedStock()
                    + " ea, 주문 수량: " + order.getQuantity() + " ea");
        }

        // 예약 재고를 출고 처리 (가용 재고는 이미 승인 시 조정됨)
        sample.setReservedStock(sample.getReservedStock() - order.getQuantity());
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

        // 예약 재고를 가용 재고로 반환
        int toReturn = Math.min(sample.getReservedStock(), order.getQuantity());
        sample.setReservedStock(sample.getReservedStock() - toReturn);
        sample.setStock(sample.getStock() + toReturn);
        sampleRepository.save(sample);

        // 반환 후 현재 가용 재고 기준으로 부족분 재계산
        int shortage = order.getQuantity() - sample.getStock();
        if (shortage <= 0) {
            throw new IllegalStateException(
                    "재고가 충분합니다. 재생산이 필요하지 않습니다.");
        }

        // 가용 재고를 예약으로 다시 이동
        int newReserve = Math.min(sample.getStock(), order.getQuantity());
        if (newReserve > 0) {
            sample.setReservedStock(sample.getReservedStock() + newReserve);
            sample.setStock(sample.getStock() - newReserve);
            sampleRepository.save(sample);
        }

        order.transition(OrderStatus.PRODUCING);
        orderRepository.save(order);

        if (productionLineService != null) {
            productionLineService.enqueue(order, shortage);
        }

        return order;
    }
}
