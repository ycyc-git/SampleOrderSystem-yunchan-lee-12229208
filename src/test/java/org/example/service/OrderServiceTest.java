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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    @TempDir
    Path tempDir;

    private SampleRepository sampleRepo;
    private OrderRepository orderRepo;
    private OrderService service;

    @BeforeEach
    void setUp() {
        sampleRepo = new SampleRepository(tempDir.resolve("samples.json").toString());
        orderRepo = new OrderRepository(tempDir.resolve("orders.json").toString(), sampleRepo);
        service = new OrderService(orderRepo, sampleRepo);
        sampleRepo.add(new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100));
        sampleRepo.add(new Sample("S-002", "GaN 에피택셀", 0.3, 0.78, 50));
    }

    // ── reserve ───────────────────────────────────────────────────

    @Test
    void reserve_createsOrderWithReservedStatus() {
        Order o = service.reserve("S-001", "삼성전자", 30);
        assertEquals(OrderStatus.RESERVED, o.getStatus());
        assertEquals("삼성전자", o.getCustomerName());
        assertEquals(30, o.getQuantity());
        assertEquals("S-001", o.getSample().getId());
        assertNotNull(o.getCreatedAt());
        assertNull(o.getReleasedAt());
    }

    @Test
    void reserve_persistsOrder() {
        Order o = service.reserve("S-001", "삼성전자", 30);
        assertTrue(orderRepo.findById(o.getOrderId()).isPresent());
    }

    @Test
    void reserve_throwsForUnknownSampleId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reserve("X-999", "고객", 10));
        assertEquals("등록되지 않은 시료 ID입니다.", ex.getMessage());
    }

    @Test
    void reserve_throwsForBlankCustomerName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reserve("S-001", "", 10));
        assertEquals("고객명을 입력하세요.", ex.getMessage());
    }

    @Test
    void reserve_throwsForNullCustomerName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("S-001", null, 10));
    }

    @Test
    void reserve_throwsForZeroQuantity() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reserve("S-001", "고객", 0));
        assertEquals("주문 수량은 1 이상이어야 합니다.", ex.getMessage());
    }

    @Test
    void reserve_throwsForNegativeQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("S-001", "고객", -5));
    }

    @Test
    void reserve_orderIdContainsTodaysDate() {
        Order o = service.reserve("S-001", "고객", 10);
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertTrue(o.getOrderId().contains(dateStr),
                "Order ID should contain today's date: " + o.getOrderId());
    }

    @Test
    void reserve_orderIdStartsWithORD() {
        Order o = service.reserve("S-001", "고객", 10);
        assertTrue(o.getOrderId().startsWith("ORD-"));
    }

    @Test
    void reserve_generatesIncrementalSerialNumbers() {
        Order o1 = service.reserve("S-001", "고객1", 10);
        Order o2 = service.reserve("S-001", "고객2", 20);
        Order o3 = service.reserve("S-002", "고객3", 5);

        assertNotEquals(o1.getOrderId(), o2.getOrderId());
        assertNotEquals(o2.getOrderId(), o3.getOrderId());
        assertTrue(o1.getOrderId().endsWith("0001"));
        assertTrue(o2.getOrderId().endsWith("0002"));
        assertTrue(o3.getOrderId().endsWith("0003"));
    }

    // ── findSampleById ────────────────────────────────────────────

    @Test
    void findSampleById_returnsExistingSample() {
        assertTrue(service.findSampleById("S-001").isPresent());
        assertEquals("실리콘 웨이퍼", service.findSampleById("S-001").get().getName());
    }

    @Test
    void findSampleById_returnsEmptyForUnknown() {
        assertTrue(service.findSampleById("X-999").isEmpty());
    }

    // ── getTotalOrders ────────────────────────────────────────────

    @Test
    void getTotalOrders_countsAllNonRejectedOrders() {
        service.reserve("S-001", "고객1", 10);
        service.reserve("S-001", "고객2", 20);
        assertEquals(2, service.getTotalOrders());
    }

    @Test
    void getTotalOrders_excludesRejectedOrders() {
        Order o1 = service.reserve("S-001", "고객1", 10);
        Order o2 = service.reserve("S-001", "고객2", 20);
        o2.transition(OrderStatus.REJECTED);
        orderRepo.save(o2);

        assertEquals(1, service.getTotalOrders());
    }

    @Test
    void getTotalOrders_returnsZero_whenEmpty() {
        assertEquals(0, service.getTotalOrders());
    }

    // ── getReservedOrders ─────────────────────────────────────────

    @Test
    void getReservedOrders_returnsOnlyReserved() {
        Order o1 = service.reserve("S-001", "고객1", 10);
        Order o2 = service.reserve("S-001", "고객2", 20);
        o2.transition(OrderStatus.CONFIRMED);
        orderRepo.save(o2);

        List<Order> reserved = service.getReservedOrders();
        assertEquals(1, reserved.size());
        assertEquals(o1.getOrderId(), reserved.get(0).getOrderId());
    }

    @Test
    void getReservedOrders_returnsEmpty_whenNone() {
        assertTrue(service.getReservedOrders().isEmpty());
    }

    // ── analyzeStock ──────────────────────────────────────────────

    @Test
    void analyzeStock_sufficientStock_zeroShortage() {
        Order o = service.reserve("S-001", "고객", 30); // stock=100, qty=30
        OrderService.StockAnalysisResult r = service.analyzeStock(o.getOrderId());

        assertEquals(100, r.currentStock);
        assertEquals(30, r.quantity);
        assertEquals(0, r.shortage);
        assertEquals(0, r.actualQty);
        assertEquals(0.0, r.totalTime, 0.001);
    }

    @Test
    void analyzeStock_exactMatch_zeroShortage() {
        sampleRepo.add(new Sample("S-100", "정확일치", 1.0, 0.90, 30));
        Order o = service.reserve("S-100", "고객", 30);
        OrderService.StockAnalysisResult r = service.analyzeStock(o.getOrderId());

        assertEquals(0, r.shortage);
    }

    @Test
    void analyzeStock_insufficientStock_computesActualQty() {
        // stock=50, qty=100, shortage=50, yield=0.78
        // actualQty = ceil(50 / (0.78 * 0.9)) = ceil(50 / 0.702) = ceil(71.22) = 72
        Order o = service.reserve("S-002", "고객", 100);
        OrderService.StockAnalysisResult r = service.analyzeStock(o.getOrderId());

        assertEquals(50, r.currentStock);
        assertEquals(100, r.quantity);
        assertEquals(50, r.shortage);
        assertEquals((int) Math.ceil(50.0 / (0.78 * 0.9)), r.actualQty);
        assertEquals(0.3 * r.actualQty, r.totalTime, 0.001);
    }

    @Test
    void analyzeStock_zeroStock() {
        sampleRepo.add(new Sample("S-200", "제로재고", 0.5, 0.90, 0));
        Order o = service.reserve("S-200", "고객", 20);
        OrderService.StockAnalysisResult r = service.analyzeStock(o.getOrderId());

        assertEquals(0, r.currentStock);
        assertEquals(20, r.shortage);
        assertTrue(r.actualQty > 0);
    }

    @Test
    void analyzeStock_throwsForUnknownOrderId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.analyzeStock("ORD-UNKNOWN-0000"));
    }

    // ── approve ───────────────────────────────────────────────────

    @Test
    void approve_sufficientStock_transitionsToConfirmed() {
        Order o = service.reserve("S-001", "고객", 30); // stock=100
        Order updated = service.approve(o.getOrderId());
        assertEquals(OrderStatus.CONFIRMED, updated.getStatus());
    }

    @Test
    void approve_sufficientStock_deductsStockFromSample() {
        Order o = service.reserve("S-001", "고객", 30); // stock=100
        service.approve(o.getOrderId());
        assertEquals(70, sampleRepo.findById("S-001").get().getStock());
    }

    @Test
    void approve_insufficientStock_transitionsToProducing() {
        Order o = service.reserve("S-002", "고객", 200); // stock=50, qty=200
        Order updated = service.approve(o.getOrderId());
        assertEquals(OrderStatus.PRODUCING, updated.getStatus());
    }

    @Test
    void approve_insufficientStock_doesNotDeductStock() {
        Order o = service.reserve("S-002", "고객", 200); // stock=50
        service.approve(o.getOrderId());
        assertEquals(50, sampleRepo.findById("S-002").get().getStock());
    }

    @Test
    void approve_persistsUpdatedOrder() {
        Order o = service.reserve("S-001", "고객", 30);
        service.approve(o.getOrderId());
        assertEquals(OrderStatus.CONFIRMED, orderRepo.findById(o.getOrderId()).get().getStatus());
    }

    @Test
    void approve_throwsForUnknownOrderId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.approve("ORD-UNKNOWN-0000"));
    }

    // ── reject ────────────────────────────────────────────────────

    @Test
    void reject_transitionsToRejected() {
        Order o = service.reserve("S-001", "고객", 30);
        Order updated = service.reject(o.getOrderId());
        assertEquals(OrderStatus.REJECTED, updated.getStatus());
    }

    @Test
    void reject_doesNotDeductStock() {
        Order o = service.reserve("S-001", "고객", 30);
        service.reject(o.getOrderId());
        assertEquals(100, sampleRepo.findById("S-001").get().getStock());
    }

    @Test
    void reject_persistsUpdatedOrder() {
        Order o = service.reserve("S-001", "고객", 30);
        service.reject(o.getOrderId());
        assertEquals(OrderStatus.REJECTED, orderRepo.findById(o.getOrderId()).get().getStatus());
    }

    @Test
    void reject_throwsForUnknownOrderId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reject("ORD-UNKNOWN-0000"));
    }
}
