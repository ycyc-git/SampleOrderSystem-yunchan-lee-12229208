package org.example.service;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseServiceTest {

    @TempDir
    Path tempDir;

    private SampleRepository sampleRepo;
    private OrderRepository orderRepo;
    private OrderService orderService;
    private ReleaseService releaseService;

    @BeforeEach
    void setUp() {
        sampleRepo = new SampleRepository(tempDir.resolve("samples.json").toString());
        orderRepo = new OrderRepository(tempDir.resolve("orders.json").toString(), sampleRepo);
        orderService = new OrderService(orderRepo, sampleRepo);
        releaseService = new ReleaseService(orderRepo, sampleRepo);

        sampleRepo.add(new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100));
        sampleRepo.add(new Sample("S-002", "GaN 에피택셀", 0.3, 0.78, 0));
    }

    /** RESERVED → CONFIRMED 으로 상태 강제 전환 (approve via OrderService 대신 직접 전환) */
    private Order makeConfirmedOrder(String sampleId, String customer, int qty) {
        Order o = orderService.reserve(sampleId, customer, qty);
        o.transition(OrderStatus.CONFIRMED);
        orderRepo.save(o);
        return o;
    }

    // ── getConfirmedOrders ────────────────────────────────────────

    @Test
    void getConfirmedOrders_returnsEmpty_whenNone() {
        assertTrue(releaseService.getConfirmedOrders().isEmpty());
    }

    @Test
    void getConfirmedOrders_returnsOnlyConfirmed() {
        makeConfirmedOrder("S-001", "고객A", 10);
        orderService.reserve("S-001", "고객B", 5); // RESERVED — 미포함
        List<Order> result = releaseService.getConfirmedOrders();
        assertEquals(1, result.size());
        assertEquals(OrderStatus.CONFIRMED, result.get(0).getStatus());
    }

    @Test
    void getConfirmedOrders_returnsAllConfirmed() {
        makeConfirmedOrder("S-001", "고객A", 10);
        makeConfirmedOrder("S-001", "고객B", 5);
        assertEquals(2, releaseService.getConfirmedOrders().size());
    }

    @Test
    void getConfirmedOrders_excludesReleased() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        releaseService.release(o.getOrderId());
        assertTrue(releaseService.getConfirmedOrders().isEmpty());
    }

    // ── release — 정상 케이스 ─────────────────────────────────────

    @Test
    void release_transitionsOrderToRelease() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        Order released = releaseService.release(o.getOrderId());
        assertEquals(OrderStatus.RELEASE, released.getStatus());
    }

    @Test
    void release_setsReleasedAt() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        Order released = releaseService.release(o.getOrderId());
        assertNotNull(released.getReleasedAt());
    }

    @Test
    void release_deductsStockFromSample() {
        // S-001 stock=100, qty=30 → 후 stock=70
        Order o = makeConfirmedOrder("S-001", "고객A", 30);
        releaseService.release(o.getOrderId());
        assertEquals(70, sampleRepo.findById("S-001").get().getStock());
    }

    @Test
    void release_persistsOrderStatus() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        releaseService.release(o.getOrderId());
        Order reloaded = orderRepo.findById(o.getOrderId()).get();
        assertEquals(OrderStatus.RELEASE, reloaded.getStatus());
    }

    @Test
    void release_persistsReleasedAt() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        releaseService.release(o.getOrderId());
        Order reloaded = orderRepo.findById(o.getOrderId()).get();
        assertNotNull(reloaded.getReleasedAt());
    }

    @Test
    void release_persistsDeductedStock() {
        Order o = makeConfirmedOrder("S-001", "고객A", 40);
        releaseService.release(o.getOrderId());

        SampleRepository reloadedSampleRepo =
                new SampleRepository(tempDir.resolve("samples.json").toString());
        assertEquals(60, reloadedSampleRepo.findById("S-001").get().getStock());
    }

    // ── release — 오류 케이스 ─────────────────────────────────────

    @Test
    void release_throwsOnUnknownOrderId() {
        assertThrows(IllegalArgumentException.class,
                () -> releaseService.release("ORD-UNKNOWN"));
    }

    @Test
    void release_throwsOnReservedOrder() {
        Order o = orderService.reserve("S-001", "고객A", 10);
        assertThrows(IllegalStateException.class,
                () -> releaseService.release(o.getOrderId()));
    }

    @Test
    void release_throwsOnRejectedOrder() {
        Order o = orderService.reserve("S-001", "고객A", 10);
        orderService.reject(o.getOrderId());
        assertThrows(IllegalStateException.class,
                () -> releaseService.release(o.getOrderId()));
    }

    @Test
    void release_throwsOnAlreadyReleasedOrder() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        releaseService.release(o.getOrderId());
        assertThrows(IllegalStateException.class,
                () -> releaseService.release(o.getOrderId()));
    }

    @Test
    void release_throwsWhenStockInsufficient() {
        // S-001 stock=100, 먼저 출고로 재고를 95로 줄임
        Order o1 = makeConfirmedOrder("S-001", "고객A", 95);
        releaseService.release(o1.getOrderId()); // stock=5 남음
        // 수량 10 > stock 5 → 오류
        Order o2 = makeConfirmedOrder("S-001", "고객B", 10);
        assertThrows(IllegalStateException.class,
                () -> releaseService.release(o2.getOrderId()));
    }

    // ── 연속 출고 ─────────────────────────────────────────────────

    @Test
    void release_multipleOrders_deductsStockAccumulatively() {
        Order o1 = makeConfirmedOrder("S-001", "고객A", 30);
        Order o2 = makeConfirmedOrder("S-001", "고객B", 20);
        releaseService.release(o1.getOrderId());
        releaseService.release(o2.getOrderId());
        assertEquals(50, sampleRepo.findById("S-001").get().getStock());
    }
}
