package org.example.repository;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderRepositoryTest {

    @TempDir
    Path tempDir;

    private SampleRepository sampleRepo;
    private OrderRepository orderRepo;
    private Sample sample;

    @BeforeEach
    void setUp() {
        sampleRepo = new SampleRepository(tempDir.resolve("samples.json").toString());
        orderRepo = new OrderRepository(tempDir.resolve("orders.json").toString(), sampleRepo);
        sample = new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100);
        sampleRepo.add(sample);
    }

    private Order newOrder(String orderId) {
        return new Order(orderId, sample, "테스트 고객", 10,
                OrderStatus.RESERVED, LocalDateTime.now(), null);
    }

    // ── 기본 CRUD ─────────────────────────────────────────────────

    @Test
    void save_and_findById_returnsOrder() {
        Order o = newOrder("ORD-20260101-0001");
        orderRepo.save(o);

        assertTrue(orderRepo.findById("ORD-20260101-0001").isPresent());
        assertEquals("테스트 고객", orderRepo.findById("ORD-20260101-0001")
                .get().getCustomerName());
    }

    @Test
    void findById_returnsEmpty_forUnknownId() {
        assertTrue(orderRepo.findById("ORD-XXXXX-0000").isEmpty());
    }

    @Test
    void findAll_returnsAllOrders() {
        orderRepo.save(newOrder("ORD-20260101-0001"));
        orderRepo.save(newOrder("ORD-20260101-0002"));
        assertEquals(2, orderRepo.findAll().size());
    }

    @Test
    void save_updates_existingOrder() {
        Order o = newOrder("ORD-20260101-0001");
        orderRepo.save(o);
        o.transition(OrderStatus.CONFIRMED);
        orderRepo.save(o);

        assertEquals(OrderStatus.CONFIRMED,
                orderRepo.findById("ORD-20260101-0001").get().getStatus());
    }

    // ── findByStatus ──────────────────────────────────────────────

    @Test
    void findByStatus_returnsOnlyMatchingOrders() {
        Order r1 = newOrder("ORD-20260101-0001");
        Order r2 = newOrder("ORD-20260101-0002");
        Order confirmed = newOrder("ORD-20260101-0003");
        confirmed.transition(OrderStatus.CONFIRMED);

        orderRepo.save(r1);
        orderRepo.save(r2);
        orderRepo.save(confirmed);

        List<Order> reserved = orderRepo.findByStatus(OrderStatus.RESERVED);
        assertEquals(2, reserved.size());

        List<Order> confirmedList = orderRepo.findByStatus(OrderStatus.CONFIRMED);
        assertEquals(1, confirmedList.size());
    }

    @Test
    void findByStatus_returnsEmpty_whenNoMatch() {
        orderRepo.save(newOrder("ORD-20260101-0001"));
        assertTrue(orderRepo.findByStatus(OrderStatus.CONFIRMED).isEmpty());
    }

    // ── 영속성 ────────────────────────────────────────────────────

    @Test
    void persistence_survivesRestart() {
        orderRepo.save(newOrder("ORD-20260101-0001"));
        orderRepo.save(newOrder("ORD-20260101-0002"));

        OrderRepository reloaded = new OrderRepository(
                tempDir.resolve("orders.json").toString(), sampleRepo);
        assertEquals(2, reloaded.findAll().size());
    }

    @Test
    void persistence_restoresAllFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 9, 30, 0);
        Order o = new Order("ORD-20260101-0001", sample, "삼성전자", 30,
                OrderStatus.RESERVED, createdAt, null);
        orderRepo.save(o);

        OrderRepository reloaded = new OrderRepository(
                tempDir.resolve("orders.json").toString(), sampleRepo);
        Order loaded = reloaded.findById("ORD-20260101-0001").get();

        assertEquals("ORD-20260101-0001", loaded.getOrderId());
        assertEquals("S-001", loaded.getSample().getId());
        assertEquals("삼성전자", loaded.getCustomerName());
        assertEquals(30, loaded.getQuantity());
        assertEquals(OrderStatus.RESERVED, loaded.getStatus());
        assertEquals(createdAt, loaded.getCreatedAt());
        assertNull(loaded.getReleasedAt());
    }

    @Test
    void persistence_restoresUpdatedStatus() {
        Order o = newOrder("ORD-20260101-0001");
        orderRepo.save(o);
        o.transition(OrderStatus.CONFIRMED);
        orderRepo.save(o);

        OrderRepository reloaded = new OrderRepository(
                tempDir.resolve("orders.json").toString(), sampleRepo);
        assertEquals(OrderStatus.CONFIRMED,
                reloaded.findById("ORD-20260101-0001").get().getStatus());
    }

    @Test
    void constructorWithMissingFile_startsEmpty() {
        OrderRepository fresh = new OrderRepository(
                tempDir.resolve("nonexistent.json").toString(), sampleRepo);
        assertTrue(fresh.findAll().isEmpty());
    }
}
