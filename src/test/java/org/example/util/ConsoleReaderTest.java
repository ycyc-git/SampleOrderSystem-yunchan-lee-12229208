package org.example.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleReaderTest {

    private ConsoleReader readerOf(String input) {
        return new ConsoleReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );
    }

    // ── readLine ──────────────────────────────────────────────────

    @Test
    void readLine_returnsInput() {
        assertEquals("hello", readerOf("hello\n").readLine(""));
    }

    @Test
    void readLine_trimsWhitespace() {
        assertEquals("hello", readerOf("  hello  \n").readLine(""));
    }

    @Test
    void readLine_emptyInputReturnsEmptyString() {
        assertEquals("", readerOf("\n").readLine(""));
    }

    // ── readInt ───────────────────────────────────────────────────

    @Test
    void readInt_parsesValidInteger() {
        assertEquals(42, readerOf("42\n").readInt(""));
    }

    @Test
    void readInt_retriesOnInvalidThenSucceeds() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream("abc\n42\n".getBytes(StandardCharsets.UTF_8)), out);

        assertEquals(42, reader.readInt(""));
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("숫자를 입력하세요."));
    }

    @Test
    void readInt_acceptsNegativeNumber() {
        assertEquals(-5, readerOf("-5\n").readInt(""));
    }

    // ── readDouble ────────────────────────────────────────────────

    @Test
    void readDouble_parsesValidDouble() {
        assertEquals(3.14, readerOf("3.14\n").readDouble(""), 0.001);
    }

    @Test
    void readDouble_retriesOnInvalidThenSucceeds() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);
        ConsoleReader reader = new ConsoleReader(
                new ByteArrayInputStream("xyz\n0.92\n".getBytes(StandardCharsets.UTF_8)), out);

        assertEquals(0.92, reader.readDouble(""), 0.001);
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("숫자를 입력하세요."));
    }

    @Test
    void readDouble_parsesIntegerAsDouble() {
        assertEquals(1.0, readerOf("1\n").readDouble(""), 0.001);
    }
}
