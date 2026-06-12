package org.example.controller;

import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.example.service.OrderService;
import org.example.service.SampleService;
import org.example.util.ConsoleReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OrderControllerTest {

    @TempDir
    Path tempDir;

    private SampleService sampleService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        SampleRepository sampleRepo =
                new SampleRepository(tempDir.resolve("samples.json").toString());
        OrderRepository orderRepo =
                new OrderRepository(tempDir.resolve("orders.json").toString(), sampleRepo);
        sampleService = new SampleService(sampleRepo);
        orderService = new OrderService(orderRepo, sampleRepo);
        sampleService.register("S-001", "실리콘 웨이퍼-8인치", 0.5, 0.92, 100);
        sampleService.register("S-002", "GaN 에피택셀-4인치", 0.3, 0.78, 50);
    }

    private String runWith(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), ps);
        new OrderController(orderService, reader, ps).run();
        return baos.toString(StandardCharsets.UTF_8);
    }

    // ── 입력 화면 ─────────────────────────────────────────────────

    @Test
    void run_displaysInputPrompts() {
        // sampleId → customerName → quantity → Y
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("시료 ID"));
        assertTrue(output.contains("고객명"));
        assertTrue(output.contains("수량"));
    }

    @Test
    void run_invalidSampleId_showsErrorAndRetries() {
        // X-999(invalid) → S-001(valid) → rest
        String output = runWith("X-999\nS-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("등록되지 않은 시료 ID입니다."));
        assertEquals(1, orderService.getTotalOrders());
    }

    @Test
    void run_emptyCustomerName_showsErrorAndRetries() {
        // S-001 → blank name → valid name → rest
        String output = runWith("S-001\n\n삼성전자\n30\nY\n");
        assertTrue(output.contains("고객명을 입력하세요."));
        assertEquals(1, orderService.getTotalOrders());
    }

    @Test
    void run_zeroQuantity_showsErrorAndRetries() {
        // S-001 → name → 0(invalid) → 30(valid) → Y
        String output = runWith("S-001\n삼성전자\n0\n30\nY\n");
        assertTrue(output.contains("주문 수량은 1 이상이어야 합니다."));
        assertEquals(1, orderService.getTotalOrders());
    }

    @Test
    void run_negativeQuantity_showsErrorAndRetries() {
        String output = runWith("S-001\n삼성전자\n-5\n30\nY\n");
        assertTrue(output.contains("주문 수량은 1 이상이어야 합니다."));
        assertEquals(1, orderService.getTotalOrders());
    }

    // ── 확인 화면 ─────────────────────────────────────────────────

    @Test
    void run_showsConfirmationWithSampleName() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("입력 내용 확인"));
        assertTrue(output.contains("실리콘 웨이퍼-8인치"));
        assertTrue(output.contains("S-001"));
    }

    @Test
    void run_showsConfirmationWithCustomerAndQuantity() {
        String output = runWith("S-001\n삼성전자 파운드리\n30\nY\n");
        assertTrue(output.contains("삼성전자 파운드리"));
        assertTrue(output.contains("30 ea"));
    }

    @Test
    void run_showsYNChoice() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("[Y] 예약 접수"));
        assertTrue(output.contains("[N] 취소"));
    }

    @Test
    void run_invalidConfirmInput_showsErrorAndRetries() {
        // S-001 → name → qty → X(invalid) → Y(valid)
        String output = runWith("S-001\n삼성전자\n30\nX\nY\n");
        assertTrue(output.contains("Y 또는 N을 입력하세요."));
        assertEquals(1, orderService.getTotalOrders());
    }

    // ── 취소 ──────────────────────────────────────────────────────

    @Test
    void run_cancelWithN_doesNotCreateOrder() {
        String output = runWith("S-001\n삼성전자\n30\nN\n");
        assertTrue(output.contains("주문이 취소되었습니다."));
        assertEquals(0, orderService.getTotalOrders());
    }

    @Test
    void run_cancelWithLowercaseN_doesNotCreateOrder() {
        runWith("S-001\n삼성전자\n30\nn\n");
        assertEquals(0, orderService.getTotalOrders());
    }

    // ── 완료 ──────────────────────────────────────────────────────

    @Test
    void run_confirmWithY_createsReservedOrder() {
        runWith("S-001\n삼성전자\n30\nY\n");
        assertEquals(1, orderService.getTotalOrders());
    }

    @Test
    void run_confirmWithY_displaysOrderNumber() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("ORD-"));
        assertTrue(output.contains("예약 접수 완료."));
    }

    @Test
    void run_confirmWithY_displaysReservedBadge() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("[RESERVED]"));
    }

    @Test
    void run_confirmWithY_displaysApprovalHint() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("승인 메뉴"));
    }

    @Test
    void run_multipleOrders_haveSequentialIds() {
        runWith("S-001\n고객1\n10\nY\n");
        runWith("S-002\n고객2\n20\nY\n");
        assertEquals(2, orderService.getTotalOrders());
    }

    @Test
    void run_lowercaseY_confirmsOrder() {
        runWith("S-001\n삼성전자\n30\ny\n");
        assertEquals(1, orderService.getTotalOrders());
    }
}
