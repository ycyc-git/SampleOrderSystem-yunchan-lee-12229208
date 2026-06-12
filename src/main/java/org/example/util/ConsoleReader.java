package org.example.util;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ConsoleReader {

    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleReader() {
        this.scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        this.out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    }

    public ConsoleReader(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in, StandardCharsets.UTF_8);
        this.out = out;
    }

    public PrintStream getOut() { return out; }

    public String readLine(String prompt) {
        out.print(prompt);
        return scanner.nextLine().trim();
    }

    public int readInt(String prompt) {
        while (true) {
            out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                out.println("숫자를 입력하세요.");
            }
        }
    }

    public double readDouble(String prompt) {
        while (true) {
            out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Double.parseDouble(line);
            } catch (NumberFormatException e) {
                out.println("숫자를 입력하세요.");
            }
        }
    }
}
