package org.example.controller;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.example.service.OrderService;
import org.example.service.ReleaseService;
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

class ReleaseControllerTest {

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
        sampleRepo.add(new Sample("S-002", "GaN 에피택셀", 0.3, 0.78, 50));
    }

    private String runWith(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), ps);
        new ReleaseController(releaseService, reader, ps).run();
        return baos.toString(StandardCharsets.UTF_8);
    }

    private Order makeConfirmedOrder(String sampleId, String customer, int qty) {
        Order o = orderService.reserve(sampleId, customer, qty);
        o.transition(OrderStatus.CONFIRMED);
        orderRepo.save(o);
        return o;
    }

    // ── 출고 가능 주문 없음 ───────────────────────────────────────

    @Test
    void run_noConfirmedOrders_showsEmptyMessage() {
        String output = runWith("");
        assertTrue(output.contains("출고 가능한 주문이 없습니다."));
    }

    @Test
    void run_noConfirmedOrders_doesNotShowTable() {
        String output = runWith("");
        assertFalse(output.contains("번호"));
    }

    // ── 목록 표시 ─────────────────────────────────────────────────

    @Test
    void run_showsConfirmedOrdersList() {
        makeConfirmedOrder("S-001", "삼성전자", 10);
        String output = runWith("0\n");
        assertTrue(output.contains("삼성전자"));
        assertTrue(output.contains("S-001") || output.contains("실리콘 웨이퍼"));
    }

    @Test
    void run_showsHeader() {
        makeConfirmedOrder("S-001", "고객A", 10);
        String output = runWith("0\n");
        assertTrue(output.contains("[6] 출고 처리"));
    }

    @Test
    void run_showsOrderQuantity() {
        makeConfirmedOrder("S-001", "고객A", 25);
        String output = runWith("0\n");
        assertTrue(output.contains("25"));
    }

    @Test
    void run_showsMultipleOrders() {
        makeConfirmedOrder("S-001", "고객A", 10);
        makeConfirmedOrder("S-002", "고객B", 20);
        String output = runWith("0\n");
        assertTrue(output.contains("고객A"));
        assertTrue(output.contains("고객B"));
    }

    // ── 취소 ─────────────────────────────────────────────────────

    @Test
    void run_selectZero_cancels() {
        makeConfirmedOrder("S-001", "고객A", 10);
        String output = runWith("0\n");
        assertTrue(output.contains("취소"));
    }

    @Test
    void run_selectZero_doesNotChangeOrderStatus() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        runWith("0\n");
        assertEquals(OrderStatus.CONFIRMED, orderRepo.findById(o.getOrderId()).get().getStatus());
    }

    // ── 정상 출고 ─────────────────────────────────────────────────

    @Test
    void run_selectOne_completesRelease() {
        makeConfirmedOrder("S-001", "고객A", 10);
        String output = runWith("1\n");
        assertTrue(output.contains("출고 처리 완료."));
    }

    @Test
    void run_selectOne_showsOrderId() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        String output = runWith("1\n");
        assertTrue(output.contains(o.getOrderId()));
    }

    @Test
    void run_selectOne_showsQuantity() {
        makeConfirmedOrder("S-001", "고객A", 30);
        String output = runWith("1\n");
        assertTrue(output.contains("30 ea"));
    }

    @Test
    void run_selectOne_showsReleasedAt() {
        makeConfirmedOrder("S-001", "고객A", 10);
        String output = runWith("1\n");
        assertTrue(output.contains("처리일시"));
        // yyyy-MM-dd HH:mm:ss 형식 포함 여부 (연도 4자리)
        assertTrue(output.matches("(?s).*\\d{4}-\\d{2}-\\d{2}.*"));
    }

    @Test
    void run_selectOne_showsStatusTransition() {
        makeConfirmedOrder("S-001", "고객A", 10);
        String output = runWith("1\n");
        assertTrue(output.contains("CONFIRMED") && output.contains("RELEASE"));
    }

    @Test
    void run_selectOne_transitionsOrderStatus() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        runWith("1\n");
        assertEquals(OrderStatus.RELEASE, orderRepo.findById(o.getOrderId()).get().getStatus());
    }

    @Test
    void run_selectOne_deductsStock() {
        makeConfirmedOrder("S-001", "고객A", 30); // stock=100
        runWith("1\n");
        assertEquals(70, sampleRepo.findById("S-001").get().getStock());
    }

    // ── 유효하지 않은 입력 ────────────────────────────────────────

    @Test
    void run_invalidNumber_showsErrorMessage() {
        makeConfirmedOrder("S-001", "고객A", 10);
        // abc → error, then 0 → cancel
        String output = runWith("abc\n0\n");
        assertTrue(output.contains("유효하지 않은 번호입니다."));
    }

    @Test
    void run_outOfRangeNumber_showsErrorMessage() {
        makeConfirmedOrder("S-001", "고객A", 10);
        // 99 → error, then 0 → cancel
        String output = runWith("99\n0\n");
        assertTrue(output.contains("유효하지 않은 번호입니다."));
    }

    @Test
    void run_negativeNumber_showsErrorMessage() {
        makeConfirmedOrder("S-001", "고객A", 10);
        // -1 → error, then 0 → cancel
        String output = runWith("-1\n0\n");
        assertTrue(output.contains("유효하지 않은 번호입니다."));
    }

    @Test
    void run_invalidInput_doesNotChangeOrderStatus() {
        Order o = makeConfirmedOrder("S-001", "고객A", 10);
        runWith("abc\n0\n");
        assertEquals(OrderStatus.CONFIRMED, orderRepo.findById(o.getOrderId()).get().getStatus());
    }

    @Test
    void run_invalidThenValidSelection_succeeds() {
        makeConfirmedOrder("S-001", "고객A", 10);
        // 99 → error, then 1 → release
        String output = runWith("99\n1\n");
        assertTrue(output.contains("유효하지 않은 번호입니다."));
        assertTrue(output.contains("출고 처리 완료."));
    }

    // ── 두 번째 항목 선택 ─────────────────────────────────────────

    @Test
    void run_selectSecond_releasesSecondOrder() {
        makeConfirmedOrder("S-001", "고객A", 10);
        Order o2 = makeConfirmedOrder("S-002", "고객B", 20);
        runWith("2\n");
        assertEquals(OrderStatus.RELEASE, orderRepo.findById(o2.getOrderId()).get().getStatus());
    }

    @Test
    void run_selectSecond_firstOrderStillConfirmed() {
        Order o1 = makeConfirmedOrder("S-001", "고객A", 10);
        makeConfirmedOrder("S-002", "고객B", 20);
        runWith("2\n");
        assertEquals(OrderStatus.CONFIRMED, orderRepo.findById(o1.getOrderId()).get().getStatus());
    }
}
