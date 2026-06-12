package org.example;

import org.example.util.Ansi;
import org.example.util.ConsoleReader;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class MainMenu {

    private static final String ASCII_ART =
            "███████╗     ███████╗███████╗███╗   ███╗██╗\n" +
            "██╔════╝     ██╔════╝██╔════╝████╗ ████║██║\n" +
            "███████╗████╗███████╗█████╗  ██╔████╔██║██║\n" +
            " ╚═══██║╚═══╝╚════██║██╔══╝  ██║╚██╔╝██║██║\n" +
            "███████║     ███████║███████╗██║ ╚═╝ ██║██║\n" +
            "╚══════╝     ╚══════╝╚══════╝╚═╝     ╚═╝╚═╝";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConsoleReader reader;
    private final PrintStream out;
    private final Runnable exitHandler;
    private final Map<String, Runnable> menuHandlers = new HashMap<>();

    public MainMenu(ConsoleReader reader) {
        this(reader, System.out, () -> System.exit(0));
    }

    // package-private: 테스트 전용 생성자
    MainMenu(ConsoleReader reader, PrintStream out, Runnable exitHandler) {
        this.reader = reader;
        this.out = out;
        this.exitHandler = exitHandler;
    }

    public void setMenuHandler(String key, Runnable handler) {
        menuHandlers.put(key, handler);
    }

    public void run() {
        while (true) {
            printScreen();
            String input = reader.readLine("선택 > ");
            switch (input) {
                case "0":
                    exitHandler.run();
                    return;
                case "1": case "2": case "3":
                case "4": case "5": case "6": {
                    Runnable handler = menuHandlers.get(input);
                    if (handler != null) {
                        handler.run();
                    } else {
                        out.println("\n[준비 중입니다.]\n");
                    }
                    break;
                }
                default:
                    out.println("잘못된 입력입니다. 다시 선택하세요.");
            }
        }
    }

    private void printScreen() {
        out.println(ASCII_ART);
        out.println("        반도체 시료 생산주문관리 시스템");
        out.println("================================================================");
        out.printf("시스템 현황  %s%n", LocalDateTime.now().format(FORMATTER));
        out.println();
        out.printf("등록 시료  %s%d종%s        총 재고   %s%,d ea%s%n",
                Ansi.YELLOW, getSampleCount(), Ansi.RESET,
                Ansi.GREEN,  getTotalStock(),   Ansi.RESET);
        out.printf("전체 주문  %s%d건%s        생산라인  %s%d건 대기%s%n",
                Ansi.YELLOW, getTotalOrders(),          Ansi.RESET,
                Ansi.YELLOW, getProductionQueueSize(),  Ansi.RESET);
        out.println("----------------------------------------------------------------");
        out.println("[1] 시료 관리                    [2] 시료 주문");
        out.println("[3] 주문 승인/거절               [4] 모니터링");
        out.println("[5] 생산라인 조회                [6] 출고 처리");
        out.println("[0] 종료");
        out.println("----------------------------------------------------------------");
    }

    // 이후 Phase에서 실제 값으로 교체
    protected int getSampleCount()       { return 0; }
    protected int getTotalStock()        { return 0; }
    protected int getTotalOrders()       { return 0; }
    protected int getProductionQueueSize() { return 0; }
}
