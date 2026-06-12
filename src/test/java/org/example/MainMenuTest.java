package org.example;

import org.example.util.ConsoleReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MainMenuTest {

    /**
     * 주어진 입력 문자열로 MainMenu를 실행하고 출력 전체를 반환한다.
     * exitHandler는 루프 종료만 수행(System.exit 미호출).
     */
    private String runWith(String input) throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out);
        new MainMenu(reader, out, () -> {}).run();
        return captured.toString(StandardCharsets.UTF_8);
    }

    // ── 화면 구성 ─────────────────────────────────────────────────

    @Test
    void run_displaysTitle() throws Exception {
        assertTrue(runWith("0\n").contains("반도체 시료 생산주문관리 시스템"));
    }

    @Test
    void run_displaysAllMenuNumbers() throws Exception {
        String output = runWith("0\n");
        for (String n : new String[]{"[1]", "[2]", "[3]", "[4]", "[5]", "[6]", "[0]"}) {
            assertTrue(output.contains(n), "메뉴 항목 누락: " + n);
        }
    }

    @Test
    void run_displaysSystemStatsLabels() throws Exception {
        String output = runWith("0\n");
        assertTrue(output.contains("등록 시료"));
        assertTrue(output.contains("총 재고"));
        assertTrue(output.contains("전체 주문"));
        assertTrue(output.contains("생산라인"));
    }

    @Test
    void run_statsAreZeroInPhase01() throws Exception {
        String output = runWith("0\n");
        // "등록 시료  0종" 형식으로 출력되어야 함
        assertTrue(output.contains("0종"));
        assertTrue(output.contains("0 ea"));
        assertTrue(output.contains("0건"));
    }

    // ── 메뉴 동작 ─────────────────────────────────────────────────

    @Test
    void run_zeroInput_callsExitHandler() throws Exception {
        AtomicBoolean exited = new AtomicBoolean(false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream("0\n".getBytes(StandardCharsets.UTF_8)), ps);
        new MainMenu(reader, ps, () -> exited.set(true)).run();
        assertTrue(exited.get());
    }

    @Test
    void run_validMenuInput_showsWIPMessage() throws Exception {
        for (String n : new String[]{"1", "2", "3", "4", "5", "6"}) {
            String output = runWith(n + "\n0\n");
            assertTrue(output.contains("준비 중입니다"), "[" + n + "] 선택 시 준비 중 메시지 없음");
        }
    }

    @Test
    void run_invalidInput_showsErrorThenContinues() throws Exception {
        // "9" → 오류 메시지 → "0" → 종료
        String output = runWith("9\n0\n");
        assertTrue(output.contains("잘못된 입력입니다"));
    }

    @Test
    void run_nonNumericInput_showsErrorThenContinues() throws Exception {
        String output = runWith("abc\n0\n");
        assertTrue(output.contains("잘못된 입력입니다"));
    }
}
