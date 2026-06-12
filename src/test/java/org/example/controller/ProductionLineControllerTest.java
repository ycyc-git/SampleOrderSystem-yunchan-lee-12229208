package org.example.controller;

import org.example.domain.Order;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.example.service.OrderService;
import org.example.service.ProductionLineService;
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

class ProductionLineControllerTest {

    @TempDir
    Path tempDir;

    private OrderService orderService;
    private ProductionLineService productionLineService;

    @BeforeEach
    void setUp() {
        SampleRepository sampleRepo =
                new SampleRepository(tempDir.resolve("samples.json").toString());
        OrderRepository orderRepo =
                new OrderRepository(tempDir.resolve("orders.json").toString(), sampleRepo);
        productionLineService = new ProductionLineService(
                tempDir.resolve("jobs.json").toString(), orderRepo, sampleRepo);
        orderService = new OrderService(orderRepo, sampleRepo, productionLineService);

        sampleRepo.add(new Sample("S-001", "실리콘 웨이퍼-8인치", 0.5, 0.92, 100));
        sampleRepo.add(new Sample("S-002", "GaN 에피택셀-4인치", 0.3, 0.78, 0));
        sampleRepo.add(new Sample("S-003", "SiC 파워기판", 0.8, 0.85, 0));
    }

    private String runWith(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), ps);
        new ProductionLineController(productionLineService, reader, ps).run();
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void enqueueJob(String sampleId, String customer, int qty) {
        Order o = orderService.reserve(sampleId, customer, qty);
        orderService.approve(o.getOrderId());
    }

    // ── IDLE 상태 ─────────────────────────────────────────────────

    @Test
    void run_emptyQueue_showsIdleState() {
        String output = runWith("\n");
        assertTrue(output.contains("IDLE"));
        assertTrue(output.contains("현재 생산 중인 작업이 없습니다."));
        assertTrue(output.contains("대기 중인 주문도 없습니다."));
    }

    @Test
    void run_emptyQueue_showsHeader() {
        String output = runWith("\n");
        assertTrue(output.contains("[5] 생산라인 조회"));
        assertTrue(output.contains("FIFO"));
    }

    // ── RUNNING 상태 ──────────────────────────────────────────────

    @Test
    void run_oneJob_showsRunningState() {
        enqueueJob("S-002", "고객", 50);
        String output = runWith("\n");
        assertTrue(output.contains("RUNNING"));
        assertTrue(output.contains("현재 처리 중"));
    }

    @Test
    void run_oneJob_showsOrderInfo() {
        enqueueJob("S-002", "삼성전자", 50);
        String output = runWith("\n");
        assertTrue(output.contains("GaN 에피택셀-4인치"));
        assertTrue(output.contains("50 ea"));
    }

    @Test
    void run_oneJob_showsShortageAndActualQty() {
        enqueueJob("S-002", "고객", 50);
        String output = runWith("\n");
        assertTrue(output.contains("부족"));
        assertTrue(output.contains("실생산량"));
    }

    @Test
    void run_oneJob_showsProgressBar() {
        enqueueJob("S-002", "고객", 50);
        String output = runWith("\n");
        assertTrue(output.contains("[░░░░░░░░░░]"));
        assertTrue(output.contains("0%"));
    }

    @Test
    void run_oneJob_showsEstimatedFinishTime() {
        enqueueJob("S-002", "고객", 50);
        String output = runWith("\n");
        assertTrue(output.contains("완료 예정"));
    }

    @Test
    void run_oneJob_noWaitingSection_whenQueueSizeIsOne() {
        enqueueJob("S-002", "고객", 50);
        String output = runWith("\n");
        assertTrue(output.contains("대기 중인 주문이 없습니다."));
    }

    // ── 대기 큐 ───────────────────────────────────────────────────

    @Test
    void run_multipleJobs_showsWaitingQueue() {
        enqueueJob("S-002", "고객1", 50);
        enqueueJob("S-003", "고객2", 30);
        String output = runWith("\n");
        assertTrue(output.contains("대기 중인 주문  (FIFO 순)"));
    }

    @Test
    void run_multipleJobs_showsWaitingJobInfo() {
        enqueueJob("S-002", "고객1", 50);
        enqueueJob("S-003", "고객2", 30);
        String output = runWith("\n");
        assertTrue(output.contains("SiC 파워기판"));
        assertTrue(output.contains("30 ea"));
    }

    @Test
    void run_threeJobs_showsTwoInWaitingQueue() {
        enqueueJob("S-002", "고객1", 50);
        enqueueJob("S-002", "고객2", 30);
        enqueueJob("S-003", "고객3", 20);
        String output = runWith("\n");
        // 순서 번호 1, 2 가 표시되어야 함
        assertTrue(output.contains("1 ") || output.contains("1\t"));
        assertTrue(output.contains("2 ") || output.contains("2\t"));
    }

    // ── 공통 ──────────────────────────────────────────────────────

    @Test
    void run_showsFootnotes() {
        String output = runWith("\n");
        assertTrue(output.contains("부족분 = 주문량 - 재고"));
        assertTrue(output.contains("선입선출(FIFO)"));
    }

    @Test
    void run_waitsForUserInput() {
        // 입력 없이 빈 줄 → 정상 종료 (hang 없이)
        String output = runWith("\n");
        assertNotNull(output);
    }

    @Test
    void run_showsOrderId_inCurrentJob() {
        enqueueJob("S-002", "고객", 50);
        String orderId = orderService.getReservedOrders().isEmpty()
                ? productionLineService.getCurrentJob().get().getOrder().getOrderId()
                : "";
        String output = runWith("\n");
        assertTrue(output.contains("ORD-"));
    }
}
