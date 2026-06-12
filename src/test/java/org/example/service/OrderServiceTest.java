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
}
