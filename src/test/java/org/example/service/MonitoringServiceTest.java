package org.example.service;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.domain.StockStatusDto;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringServiceTest {

    @TempDir
    Path tempDir;

    private SampleRepository sampleRepo;
    private OrderRepository orderRepo;
    private OrderService orderService;
    private MonitoringService service;

    @BeforeEach
    void setUp() {
        sampleRepo = new SampleRepository(tempDir.resolve("samples.json").toString());
        orderRepo = new OrderRepository(tempDir.resolve("orders.json").toString(), sampleRepo);
        orderService = new OrderService(orderRepo, sampleRepo);
        service = new MonitoringService(orderRepo, sampleRepo);

        sampleRepo.add(new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100));
        sampleRepo.add(new Sample("S-002", "GaN 에피택셀", 0.3, 0.78, 50));
        sampleRepo.add(new Sample("S-003", "제로재고", 0.8, 0.85, 0));
    }

    // ── getOrdersByStatus ─────────────────────────────────────────

    @Test
    void getOrdersByStatus_returnsEmpty_whenNone() {
        assertTrue(service.getOrdersByStatus(OrderStatus.RESERVED).isEmpty());
    }

    @Test
    void getOrdersByStatus_returnsReservedOrders() {
        orderService.reserve("S-001", "고객1", 10);
        orderService.reserve("S-001", "고객2", 20);
        List<Order> result = service.getOrdersByStatus(OrderStatus.RESERVED);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(o -> o.getStatus() == OrderStatus.RESERVED));
    }

    @Test
    void getOrdersByStatus_returnsOnlyMatchingStatus() {
        Order r1 = orderService.reserve("S-001", "고객1", 10);
        orderService.approve(r1.getOrderId()); // → CONFIRMED
        orderService.reserve("S-001", "고객2", 5); // RESERVED

        List<Order> confirmed = service.getOrdersByStatus(OrderStatus.CONFIRMED);
        assertEquals(1, confirmed.size());
        assertEquals(OrderStatus.CONFIRMED, confirmed.get(0).getStatus());

        List<Order> reserved = service.getOrdersByStatus(OrderStatus.RESERVED);
        assertEquals(1, reserved.size());
    }

    @Test
    void getOrdersByStatus_returnsProducingOrders() {
        Order o = orderService.reserve("S-002", "고객", 200); // stock=50 < 200 → PRODUCING
        orderService.approve(o.getOrderId());
        List<Order> result = service.getOrdersByStatus(OrderStatus.PRODUCING);
        assertEquals(1, result.size());
        assertEquals(OrderStatus.PRODUCING, result.get(0).getStatus());
    }

    // ── getOrderSummaryByStatus ───────────────────────────────────

    @Test
    void getOrderSummaryByStatus_empty_returnsEmptyMap() {
        Map<OrderStatus, Long> summary = service.getOrderSummaryByStatus();
        assertTrue(summary.isEmpty());
    }

    @Test
    void getOrderSummaryByStatus_countsReserved() {
        orderService.reserve("S-001", "고객1", 10);
        orderService.reserve("S-001", "고객2", 20);
        Map<OrderStatus, Long> summary = service.getOrderSummaryByStatus();
        assertEquals(2L, summary.getOrDefault(OrderStatus.RESERVED, 0L));
    }

    @Test
    void getOrderSummaryByStatus_excludesRejected() {
        Order o = orderService.reserve("S-001", "고객", 10);
        orderService.reject(o.getOrderId());
        Map<OrderStatus, Long> summary = service.getOrderSummaryByStatus();
        assertFalse(summary.containsKey(OrderStatus.REJECTED));
        assertEquals(0L, summary.getOrDefault(OrderStatus.RESERVED, 0L));
    }

    @Test
    void getOrderSummaryByStatus_countsMultipleStatuses() {
        Order r1 = orderService.reserve("S-001", "고객1", 10);
        Order r2 = orderService.reserve("S-001", "고객2", 30);
        orderService.approve(r1.getOrderId()); // stock 충분 → CONFIRMED
        orderService.approve(r2.getOrderId()); // stock 충분 → CONFIRMED (stock=60 remaining)

        Order r3 = orderService.reserve("S-002", "고객3", 200); // 재고 부족
        orderService.approve(r3.getOrderId()); // → PRODUCING

        Map<OrderStatus, Long> summary = service.getOrderSummaryByStatus();
        assertEquals(2L, summary.getOrDefault(OrderStatus.CONFIRMED, 0L));
        assertEquals(1L, summary.getOrDefault(OrderStatus.PRODUCING, 0L));
        assertEquals(0L, summary.getOrDefault(OrderStatus.RESERVED, 0L));
    }

    // ── getStockStatus ────────────────────────────────────────────

    @Test
    void getStockStatus_returnsOneEntryPerSample() {
        List<StockStatusDto> list = service.getStockStatus();
        assertEquals(3, list.size());
    }

    @Test
    void getStockStatus_stockLabel_여유_whenStockSufficient() {
        // S-001: stock=100, no pending → 여유
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s001 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-001"))
                .findFirst().get();
        assertEquals("여유", s001.getStockLabel());
    }

    @Test
    void getStockStatus_stockLabel_고갈_whenStockZero() {
        // S-003: stock=0 → 고갈
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s003 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-003"))
                .findFirst().get();
        assertEquals("고갈", s003.getStockLabel());
    }

    @Test
    void getStockStatus_stockLabel_부족_whenStockLessThanPending() {
        // S-002: stock=50, RESERVED demand=200 → 부족
        orderService.reserve("S-002", "고객", 200);
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s002 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-002"))
                .findFirst().get();
        assertEquals("부족", s002.getStockLabel());
    }

    @Test
    void getStockStatus_pendingDemand_includesReservedOrders() {
        orderService.reserve("S-001", "고객", 30);
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s001 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-001"))
                .findFirst().get();
        assertEquals(30, s001.getPendingDemand());
    }

    @Test
    void getStockStatus_pendingDemand_includesProducingOrders() {
        Order o = orderService.reserve("S-002", "고객", 200); // 재고 부족 → PRODUCING
        orderService.approve(o.getOrderId());
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s002 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-002"))
                .findFirst().get();
        assertEquals(200, s002.getPendingDemand());
    }

    @Test
    void getStockStatus_pendingDemand_excludesConfirmedOrders() {
        Order o = orderService.reserve("S-001", "고객", 10); // stock 충분
        orderService.approve(o.getOrderId()); // → CONFIRMED
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s001 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-001"))
                .findFirst().get();
        assertEquals(0, s001.getPendingDemand());
    }

    @Test
    void getStockStatus_remainingRate_100_whenNoPending() {
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s001 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-001"))
                .findFirst().get();
        assertEquals(100, s001.getRemainingRate());
    }

    @Test
    void getStockStatus_remainingRate_0_whenZeroStockAndNoPending() {
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s003 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-003"))
                .findFirst().get();
        assertEquals(0, s003.getRemainingRate());
    }

    @Test
    void getStockStatus_remainingRate_calculatedCorrectly() {
        // S-001: stock=100, pending=100 → rate = (int)(100*100.0/200) = 50
        orderService.reserve("S-001", "고객", 100);
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s001 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-001"))
                .findFirst().get();
        assertEquals(50, s001.getRemainingRate());
    }

    @Test
    void getStockStatus_여유_whenStockEqualsZeroAndPendingZero_isActually고갈() {
        // edge case: stock=0, pending=0 → 고갈 (stock==0 wins)
        List<StockStatusDto> list = service.getStockStatus();
        StockStatusDto s003 = list.stream()
                .filter(d -> d.getSample().getId().equals("S-003"))
                .findFirst().get();
        assertEquals("고갈", s003.getStockLabel());
        assertEquals(0, s003.getPendingDemand());
    }
}
