package org.example.controller;

import org.example.repository.SampleRepository;
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

class SampleControllerTest {

    @TempDir
    Path tempDir;

    private SampleService service;

    @BeforeEach
    void setUp() {
        SampleRepository repo =
                new SampleRepository(tempDir.resolve("samples.json").toString());
        service = new SampleService(repo);
    }

    private String runWith(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), ps);
        new SampleController(service, reader, ps).run();
        return baos.toString(StandardCharsets.UTF_8);
    }

    // ── 서브메뉴 ──────────────────────────────────────────────────

    @Test
    void run_displaysSubMenu() {
        String output = runWith("0\n");
        assertTrue(output.contains("[1] 시료 관리"));
        assertTrue(output.contains("[1] 시료 등록"));
        assertTrue(output.contains("[2] 시료 목록"));
        assertTrue(output.contains("[3] 시료 검색"));
        assertTrue(output.contains("[0] 뒤로"));
    }

    @Test
    void run_zeroInput_returns() {
        // should complete without hanging
        assertDoesNotThrow(() -> runWith("0\n"));
    }

    @Test
    void run_invalidInput_showsError() {
        String output = runWith("9\n0\n");
        assertTrue(output.contains("잘못된 입력입니다"));
    }

    // ── 시료 등록 ─────────────────────────────────────────────────

    @Test
    void registerSample_success_displaysConfirmation() {
        // 1(register) → id → name → avgTime → yield → stock → 0(back)
        String output = runWith("1\nS-001\n실리콘 웨이퍼\n0.5\n0.92\n100\n0\n");
        assertTrue(output.contains("시료가 등록되었습니다."));
        assertTrue(output.contains("S-001"));
        assertTrue(output.contains("실리콘 웨이퍼"));
        assertEquals(1, service.getAll().size());
    }

    @Test
    void registerSample_blankId_showsErrorAndRetries() {
        // blank id → error → valid id → rest of fields → exit
        String output = runWith("1\n\nS-001\n이름\n0.5\n0.9\n0\n0\n");
        assertTrue(output.contains("시료 ID를 입력하세요."));
        assertEquals(1, service.getAll().size());
    }

    @Test
    void registerSample_duplicateId_showsErrorAndRetries() {
        service.register("S-001", "기존", 0.5, 0.9, 10);

        String output = runWith("1\nS-001\nS-002\n새 시료\n0.5\n0.9\n0\n0\n");
        assertTrue(output.contains("이미 등록된 시료 ID입니다."));
        assertEquals(2, service.getAll().size());
    }

    @Test
    void registerSample_invalidYield_showsErrorAndRetries() {
        // yield > 1 → error → valid yield
        String output = runWith("1\nS-001\n이름\n0.5\n1.5\n0.9\n0\n0\n");
        assertTrue(output.contains("수율은 0 초과 1 이하의 값을 입력하세요."));
        assertEquals(1, service.getAll().size());
    }

    @Test
    void registerSample_zeroAvgTime_showsErrorAndRetries() {
        String output = runWith("1\nS-001\n이름\n0\n0.5\n0.9\n0\n0\n");
        assertTrue(output.contains("평균 생산시간은 0보다 커야 합니다."));
        assertEquals(1, service.getAll().size());
    }

    @Test
    void registerSample_yieldExactlyOne_succeeds() {
        String output = runWith("1\nS-001\n이름\n0.5\n1.0\n0\n0\n");
        assertTrue(output.contains("시료가 등록되었습니다."));
    }

    // ── 시료 목록 ─────────────────────────────────────────────────

    @Test
    void listSamples_displaysEmptyMessage_whenNoSamples() {
        String output = runWith("2\n0\n");
        assertTrue(output.contains("등록된 시료가 없습니다."));
    }

    @Test
    void listSamples_displaysSampleRows() {
        service.register("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100);
        service.register("S-002", "GaN 에피택셀", 0.3, 0.78, 50);

        // enter list → any key to exit list → 0 to exit menu
        String output = runWith("2\n\n0\n");
        assertTrue(output.contains("S-001"));
        assertTrue(output.contains("실리콘 웨이퍼"));
        assertTrue(output.contains("S-002"));
        assertTrue(output.contains("총 2종"));
    }

    @Test
    void listSamples_showsNextPageOption_whenMoreThan5Samples() {
        for (int i = 1; i <= 6; i++) {
            service.register("S-00" + i, "시료" + i, 0.5, 0.9, i * 10);
        }

        // enter list → N(next page) → any key to exit → 0 to exit menu
        String output = runWith("2\nN\n\n0\n");
        assertTrue(output.contains("[N] 다음페이지"));
        assertTrue(output.contains("[P] 이전페이지"));
        assertTrue(output.contains("총 6종"));
    }

    @Test
    void listSamples_firstPage_showsFirst5() {
        for (int i = 1; i <= 7; i++) {
            service.register("S-00" + i, "시료" + i, 0.5, 0.9, 0);
        }

        String output = runWith("2\n\n0\n");
        assertTrue(output.contains("S-001"));
        assertTrue(output.contains("S-005"));
        assertFalse(output.split("선택 > ")[1].contains("S-006"));
    }

    @Test
    void listSamples_nextPage_showsRemainingSamples() {
        for (int i = 1; i <= 6; i++) {
            service.register("S-00" + i, "시료" + i, 0.5, 0.9, 0);
        }

        // enter list → N(next) → any key → 0 exit
        String output = runWith("2\nN\n\n0\n");
        assertTrue(output.contains("S-006"));
    }

    // ── 시료 검색 ─────────────────────────────────────────────────

    @Test
    void searchSamples_displaysMatchingResults() {
        service.register("S-001", "실리콘 웨이퍼-8인치", 0.5, 0.92, 100);
        service.register("S-002", "GaN 에피택셀-4인치", 0.3, 0.78, 50);

        // 3(search) → keyword → 0(back)
        String output = runWith("3\n웨이퍼\n0\n");
        assertTrue(output.contains("S-001"));
        assertTrue(output.contains("실리콘 웨이퍼-8인치"));
        assertFalse(output.contains("S-002"));
    }

    @Test
    void searchSamples_displaysNoResultMessage_whenNoMatch() {
        service.register("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100);

        String output = runWith("3\n없는값\n0\n");
        assertTrue(output.contains("검색 결과가 없습니다."));
        assertTrue(output.contains("없는값"));
    }

    @Test
    void searchSamples_isCaseInsensitive() {
        service.register("S-001", "Silicon Wafer", 0.5, 0.9, 10);
        service.register("S-002", "GaN Epi", 0.3, 0.8, 20);

        String output = runWith("3\nGAN\n0\n");
        assertTrue(output.contains("S-002"));
        assertFalse(output.contains("S-001"));
    }

    @Test
    void searchSamples_emptyKeyword_returnsAll() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        service.register("S-002", "B", 0.3, 0.8, 20);

        String output = runWith("3\n\n0\n");
        assertTrue(output.contains("S-001"));
        assertTrue(output.contains("S-002"));
    }

    @Test
    void searchSamples_displaysTableHeader() {
        service.register("S-001", "실리콘 웨이퍼", 0.5, 0.9, 10);

        String output = runWith("3\n웨이퍼\n0\n");
        assertTrue(output.contains("평균 생산시간"));
        assertTrue(output.contains("현재 재고"));
    }
}
