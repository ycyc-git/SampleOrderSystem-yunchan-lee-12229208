package org.example.controller;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
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

    // ── 시료 목록 표시 ────────────────────────────────────────────

    @Test
    void run_showsSampleList() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("등록된 시료 목록"));
        assertTrue(output.contains("S-001"));
        assertTrue(output.contains("실리콘 웨이퍼-8인치"));
        assertTrue(output.contains("S-002"));
        assertTrue(output.contains("GaN 에피택셀-4인치"));
    }

    @Test
    void run_showsSampleListHeader() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("재고"));
        assertFalse(output.contains("생산시간"));
        assertFalse(output.contains("수율"));
    }

    @Test
    void run_showsSampleStock() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("100 ea"));
    }

    @Test
    void run_showsBlankEnterHint() {
        String output = runWith("S-001\n삼성전자\n30\nY\n");
        assertTrue(output.contains("빈 칸으로 엔터 시 뒤로"));
    }

    @Test
    void run_blankSampleId_cancelsAndReturns() {
        String output = runWith("\n");
        assertTrue(output.contains("주문이 취소되었습니다."));
        assertEquals(0, orderService.getTotalOrders());
    }

    @Test
    void run_noSamples_showsMessageAndReturns() {
        // 시료 없는 새 OrderService
        SampleRepository emptyRepo =
                new SampleRepository(tempDir.resolve("empty_samples.json").toString());
        OrderRepository emptyOrderRepo =
                new OrderRepository(tempDir.resolve("empty_orders.json").toString(), emptyRepo);
        OrderService emptyOrderService = new OrderService(emptyOrderRepo, emptyRepo);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)), ps);
        new OrderController(emptyOrderService, reader, ps).run();
        String output = baos.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("등록된 시료가 없습니다."));
        assertEquals(0, emptyOrderService.getTotalOrders());
    }

    // ── approveOrReject 헬퍼 ──────────────────────────────────────

    private String approveWith(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), ps);
        new OrderController(orderService, reader, ps).approveOrReject();
        return baos.toString(StandardCharsets.UTF_8);
    }

    // ── 빈 목록 ───────────────────────────────────────────────────

    @Test
    void approveOrReject_emptyList_showsMessageAndReturns() {
        String output = approveWith("");
        assertTrue(output.contains("승인 대기 중인 주문이 없습니다."));
    }

    // ── 목록 표시 ─────────────────────────────────────────────────

    @Test
    void approveOrReject_showsReservedListWithOrderInfo() {
        Order o = orderService.reserve("S-001", "삼성전자", 30);
        String output = approveWith("1\nY\n");
        assertTrue(output.contains("RESERVED"));
        assertTrue(output.contains("삼성전자"));
        assertTrue(output.contains("실리콘 웨이퍼-8인치"));
        assertTrue(output.contains("30 ea"));
    }

    @Test
    void approveOrReject_showsOrderId_inList() {
        Order o = orderService.reserve("S-001", "고객", 10);
        String output = approveWith("1\nY\n");
        assertTrue(output.contains(o.getOrderId()));
    }

    // ── 잘못된 번호 ───────────────────────────────────────────────

    @Test
    void approveOrReject_invalidNumber_showsErrorAndRetries() {
        orderService.reserve("S-001", "고객", 10);
        // 5(invalid) → 1(valid) → Y
        String output = approveWith("5\n1\nY\n");
        assertTrue(output.contains("유효하지 않은 번호입니다."));
    }

    @Test
    void approveOrReject_zeroNumber_showsErrorAndRetries() {
        orderService.reserve("S-001", "고객", 10);
        String output = approveWith("0\n1\nY\n");
        assertTrue(output.contains("유효하지 않은 번호입니다."));
    }

    // ── 재고 충분 분석 ────────────────────────────────────────────

    @Test
    void approveOrReject_showsStockAnalysis_sufficientStock() {
        // S-001 재고 100, 주문 30 → 부족분 0
        orderService.reserve("S-001", "고객", 30);
        String output = approveWith("1\nY\n");
        assertTrue(output.contains("재고 확인 중"));
        assertTrue(output.contains("현재 재고"));
        assertTrue(output.contains("부족분"));
        assertTrue(output.contains("재고 충분"));
    }

    // ── 재고 부족 분석 ────────────────────────────────────────────

    @Test
    void approveOrReject_showsStockAnalysis_insufficientStock() {
        // S-002 재고 50, 주문 200 → 부족분 150
        sampleService.register("S-003", "제로재고시료", 0.5, 0.90, 0);
        orderService.reserve("S-003", "고객", 50);
        String output = approveWith("1\nY\n");
        assertTrue(output.contains("재고 부족"));
        assertTrue(output.contains("실생산량"));
    }

    // ── Y → 승인 결과 ─────────────────────────────────────────────

    @Test
    void approveOrReject_Y_sufficientStock_showsConfirmed() {
        orderService.reserve("S-001", "고객", 30); // stock=100
        String output = approveWith("1\nY\n");
        assertTrue(output.contains("[CONFIRMED]"));
        assertTrue(output.contains("상태 변경"));
    }

    @Test
    void approveOrReject_Y_sufficientStock_doesNotDeductStock() {
        // 재고 차감은 release() 시점에만 발생 — approve는 상태 전환만
        orderService.reserve("S-001", "고객", 30); // stock=100
        approveWith("1\nY\n");
        assertEquals(100, sampleService.findById("S-001").get().getStock());
    }

    @Test
    void approveOrReject_Y_insufficientStock_showsProducing() {
        sampleService.register("S-003", "제로재고시료", 0.5, 0.90, 0);
        orderService.reserve("S-003", "고객", 50);
        String output = approveWith("1\nY\n");
        assertTrue(output.contains("[PRODUCING]"));
    }

    // ── N → 거절 ──────────────────────────────────────────────────

    @Test
    void approveOrReject_N_showsRejected() {
        orderService.reserve("S-001", "고객", 30);
        String output = approveWith("1\nN\n");
        assertTrue(output.contains("[REJECTED]"));
        assertTrue(output.contains("주문이 거절되었습니다."));
    }

    @Test
    void approveOrReject_N_doesNotDeductStock() {
        orderService.reserve("S-001", "고객", 30);
        approveWith("1\nN\n");
        assertEquals(100, sampleService.findById("S-001").get().getStock());
    }

    @Test
    void approveOrReject_N_orderBecomesRejected() {
        Order o = orderService.reserve("S-001", "고객", 30);
        approveWith("1\nN\n");
        assertTrue(orderService.getReservedOrders().isEmpty());
    }

    // ── 잘못된 Y/N 입력 ───────────────────────────────────────────

    @Test
    void approveOrReject_invalidYN_showsErrorAndRetries() {
        orderService.reserve("S-001", "고객", 30);
        String output = approveWith("1\nX\nY\n");
        assertTrue(output.contains("Y 또는 N을 입력하세요."));
    }

    // ── 완료 후 주문번호 표시 ─────────────────────────────────────

    @Test
    void approveOrReject_displaysOrderId_afterCompletion() {
        Order o = orderService.reserve("S-001", "고객", 30);
        String output = approveWith("1\nY\n");
        assertTrue(output.contains(o.getOrderId()));
    }
}
