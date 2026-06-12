package org.example.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private Sample sample;

    @BeforeEach
    void setUp() {
        sample = new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100);
    }

    private Order orderWith(OrderStatus status) {
        return new Order("ORD-20260101-0001", sample, "고객", 10,
                status, LocalDateTime.now(), null);
    }

    // ── 유효한 전이 ───────────────────────────────────────────────

    @Test
    void transition_RESERVED_to_CONFIRMED() {
        Order o = orderWith(OrderStatus.RESERVED);
        assertDoesNotThrow(() -> o.transition(OrderStatus.CONFIRMED));
        assertEquals(OrderStatus.CONFIRMED, o.getStatus());
    }

    @Test
    void transition_RESERVED_to_PRODUCING() {
        Order o = orderWith(OrderStatus.RESERVED);
        assertDoesNotThrow(() -> o.transition(OrderStatus.PRODUCING));
        assertEquals(OrderStatus.PRODUCING, o.getStatus());
    }

    @Test
    void transition_RESERVED_to_REJECTED() {
        Order o = orderWith(OrderStatus.RESERVED);
        assertDoesNotThrow(() -> o.transition(OrderStatus.REJECTED));
        assertEquals(OrderStatus.REJECTED, o.getStatus());
    }

    @Test
    void transition_PRODUCING_to_CONFIRMED() {
        Order o = orderWith(OrderStatus.PRODUCING);
        assertDoesNotThrow(() -> o.transition(OrderStatus.CONFIRMED));
        assertEquals(OrderStatus.CONFIRMED, o.getStatus());
    }

    @Test
    void transition_CONFIRMED_to_RELEASE() {
        Order o = orderWith(OrderStatus.CONFIRMED);
        assertDoesNotThrow(() -> o.transition(OrderStatus.RELEASE));
        assertEquals(OrderStatus.RELEASE, o.getStatus());
    }

    // ── 불허 전이 ─────────────────────────────────────────────────

    @Test
    void transition_REJECTED_to_CONFIRMED_throws() {
        Order o = orderWith(OrderStatus.REJECTED);
        assertThrows(IllegalStateException.class,
                () -> o.transition(OrderStatus.CONFIRMED));
    }

    @Test
    void transition_REJECTED_to_RESERVED_throws() {
        Order o = orderWith(OrderStatus.REJECTED);
        assertThrows(IllegalStateException.class,
                () -> o.transition(OrderStatus.RESERVED));
    }

    @Test
    void transition_RELEASE_to_CONFIRMED_throws() {
        Order o = orderWith(OrderStatus.RELEASE);
        assertThrows(IllegalStateException.class,
                () -> o.transition(OrderStatus.CONFIRMED));
    }

    @Test
    void transition_PRODUCING_to_REJECTED_throws() {
        Order o = orderWith(OrderStatus.PRODUCING);
        assertThrows(IllegalStateException.class,
                () -> o.transition(OrderStatus.REJECTED));
    }

    @Test
    void transition_CONFIRMED_to_PRODUCING_throws() {
        Order o = orderWith(OrderStatus.CONFIRMED);
        assertThrows(IllegalStateException.class,
                () -> o.transition(OrderStatus.PRODUCING));
    }

    @Test
    void transition_CONFIRMED_to_RESERVED_throws() {
        Order o = orderWith(OrderStatus.CONFIRMED);
        assertThrows(IllegalStateException.class,
                () -> o.transition(OrderStatus.RESERVED));
    }

    // ── getters / setters ─────────────────────────────────────────

    @Test
    void getters_returnConstructorValues() {
        LocalDateTime now = LocalDateTime.now();
        Order o = new Order("ORD-001", sample, "삼성전자", 30,
                OrderStatus.RESERVED, now, null);

        assertEquals("ORD-001", o.getOrderId());
        assertSame(sample, o.getSample());
        assertEquals("삼성전자", o.getCustomerName());
        assertEquals(30, o.getQuantity());
        assertEquals(OrderStatus.RESERVED, o.getStatus());
        assertEquals(now, o.getCreatedAt());
        assertNull(o.getReleasedAt());
    }

    @Test
    void setReleasedAt_updatesField() {
        Order o = orderWith(OrderStatus.CONFIRMED);
        LocalDateTime now = LocalDateTime.now();
        o.setReleasedAt(now);
        assertEquals(now, o.getReleasedAt());
    }
}
