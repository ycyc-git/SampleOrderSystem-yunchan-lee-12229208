package org.example.controller;

import org.example.domain.Order;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.example.service.MonitoringService;
import org.example.service.OrderService;
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

class MonitoringControllerTest {

    @TempDir
    Path tempDir;

    private SampleRepository sampleRepo;
    private OrderService orderService;
    private MonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        sampleRepo = new SampleRepository(tempDir.resolve("samples.json").toString());
        OrderRepository orderRepo =
                new OrderRepository(tempDir.resolve("orders.json").toString(), sampleRepo);
        orderService = new OrderService(orderRepo, sampleRepo);
        monitoringService = new MonitoringService(orderRepo, sampleRepo);

        sampleRepo.add(new Sample("S-001", "실리콘 웨이퍼-8인치", 0.5, 0.92, 100));
        sampleRepo.add(new Sample("S-002", "GaN 에피택셀-4인치", 0.3, 0.78, 50));
        sampleRepo.add(new Sample("S-003", "제로재고시료", 0.8, 0.85, 0));
    }

    private String runWith(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), ps);
        new MonitoringController(monitoringService, reader, ps).run();
        return baos.toString(StandardCharsets.UTF_8);
    }

    // ── 서브메뉴 ──────────────────────────────────────────────────

    @Test
    void run_zeroBack_exits() {
        String output = runWith("0\n");
        assertTrue(output.contains("[4] 모니터링"));
    }

    @Test
    void run_invalidInput_showsErrorAndRetries() {
        String output = runWith("9\n0\n");
        assertTrue(output.contains("잘못된 입력입니다."));
    }

    @Test
    void run_showsSubMenuOptions() {
        String output = runWith("0\n");
        assertTrue(output.contains("[1] 주문량 확인"));
        assertTrue(output.contains("[2] 재고량 확인"));
        assertTrue(output.contains("[0] 뒤로"));
    }

    // ── [1] 주문량 확인 ────────────────────────────────────────────

    @Test
    void run_option1_showsOrderSummarySection() {
        String output = runWith("1\n0\n");
        assertTrue(output.contains("상태별 주문 현황"));
    }

    @Test
    void run_option1_showsAllFourStatuses() {
        String output = runWith("1\n0\n");
        assertTrue(output.contains("RESERVED"));
        assertTrue(output.contains("CONFIRMED"));
        assertTrue(output.contains("PRODUCING"));
        assertTrue(output.contains("RELEASE"));
    }

    @Test
    void run_option1_doesNotShowRejected() {
        Order o = orderService.reserve("S-001", "고객", 10);
        orderService.reject(o.getOrderId());
        String output = runWith("1\n0\n");
        assertFalse(output.contains("REJECTED"));
    }

    @Test
    void run_option1_showsProducingNote() {
        String output = runWith("1\n0\n");
        assertTrue(output.contains("← 생산라인 대기"));
    }

    @Test
    void run_option1_showsCorrectReservedCount() {
        orderService.reserve("S-001", "고객1", 10);
        orderService.reserve("S-001", "고객2", 20);
        String output = runWith("1\n0\n");
        assertTrue(output.contains("2건"));
    }

    @Test
    void run_option1_showsStockSection() {
        String output = runWith("1\n0\n");
        assertTrue(output.contains("재고 현황"));
    }

    @Test
    void run_option1_showsSampleNames() {
        String output = runWith("1\n0\n");
        assertTrue(output.contains("실리콘 웨이퍼-8인치"));
        assertTrue(output.contains("GaN 에피택셀-4인치"));
        assertTrue(output.contains("제로재고시료"));
    }

    // ── [2] 재고량 확인 ────────────────────────────────────────────

    @Test
    void run_option2_showsStockSection() {
        String output = runWith("2\n0\n");
        assertTrue(output.contains("재고 현황"));
    }

    @Test
    void run_option2_doesNotShowOrderSummary() {
        String output = runWith("2\n0\n");
        assertFalse(output.contains("상태별 주문 현황"));
    }

    @Test
    void run_option2_showsSampleNames() {
        String output = runWith("2\n0\n");
        assertTrue(output.contains("실리콘 웨이퍼-8인치"));
    }

    // ── 재고 레이블 ───────────────────────────────────────────────

    @Test
    void run_stockLabel_여유_forSufficientStock() {
        String output = runWith("2\n0\n");
        assertTrue(output.contains("여유"));
    }

    @Test
    void run_stockLabel_고갈_forZeroStock() {
        String output = runWith("2\n0\n");
        assertTrue(output.contains("고갈"));
    }

    @Test
    void run_stockLabel_부족_whenStockLessThanPending() {
        orderService.reserve("S-002", "고객", 200); // pending > stock(50)
        String output = runWith("2\n0\n");
        assertTrue(output.contains("부족"));
    }

    // ── 진행 바 ───────────────────────────────────────────────────

    @Test
    void run_progressBar_allFilled_for100Percent() {
        // S-001: stock=100, no pending → 100%
        String output = runWith("2\n0\n");
        assertTrue(output.contains("██████████"));
    }

    @Test
    void run_progressBar_allEmpty_for0Percent() {
        // S-003: stock=0 → 0%
        String output = runWith("2\n0\n");
        assertTrue(output.contains("░░░░░░░░░░"));
    }

    @Test
    void run_progressBar_mixed_forPartialPercent() {
        // S-001: stock=100, pending=100 → 50% → 5 filled, 5 empty
        orderService.reserve("S-001", "고객", 100);
        String output = runWith("2\n0\n");
        assertTrue(output.contains("█████░░░░░"));
        assertTrue(output.contains("50%"));
    }

    // ── 연속 선택 ─────────────────────────────────────────────────

    @Test
    void run_multipleSelections_worksCorrectly() {
        String output = runWith("2\n1\n0\n");
        // 두 번 선택 후 0으로 종료 — 재고 현황이 2번 출력됨
        assertEquals(2, countOccurrences(output, "재고 현황"));
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
