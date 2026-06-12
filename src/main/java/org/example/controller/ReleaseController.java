package org.example.controller;

import org.example.domain.Order;
import org.example.service.ReleaseService;
import org.example.util.ConsoleReader;

import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReleaseController {

    private static final String RESET  = "[0m";
    private static final String GREEN  = "[92m";
    private static final String CYAN   = "[96m";
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReleaseService releaseService;
    private final ConsoleReader reader;
    private final PrintStream out;

    public ReleaseController(ReleaseService releaseService,
                             ConsoleReader reader, PrintStream out) {
        this.releaseService = releaseService;
        this.reader = reader;
        this.out = out;
    }

    public void run() {
        out.println("================================================================");
        out.println("[6] 출고 처리");
        out.println("----------------------------------------------------------------");

        List<Order> confirmed = releaseService.getConfirmedOrders();

        if (confirmed.isEmpty()) {
            out.println("출고 가능한 주문이 없습니다.");
            out.println("================================================================");
            return;
        }

        out.printf("%-5s %-14s %-12s %-18s %s%n",
                "번호", "주문번호", "고객", "시료", "수량");
        out.println("----- -------------- ------------ ------------------ ----------");
        for (int i = 0; i < confirmed.size(); i++) {
            Order o = confirmed.get(i);
            out.printf("%-5d %-14s %-12s %-18s %d ea%n",
                    i + 1,
                    o.getOrderId(),
                    o.getCustomerName(),
                    o.getSample().getName(),
                    o.getQuantity());
        }
        out.println("----------------------------------------------------------------");

        String input = reader.readLine("출고할 주문 번호 선택 (0=취소) > ").trim();

        if (input.equals("0")) {
            out.println("출고 처리를 취소했습니다.");
            out.println("================================================================");
            return;
        }

        int idx;
        try {
            idx = Integer.parseInt(input) - 1;
        } catch (NumberFormatException e) {
            out.println("유효하지 않은 번호입니다.");
            out.println("================================================================");
            return;
        }

        if (idx < 0 || idx >= confirmed.size()) {
            out.println("유효하지 않은 번호입니다.");
            out.println("================================================================");
            return;
        }

        Order target = confirmed.get(idx);

        try {
            Order released = releaseService.release(target.getOrderId());
            out.println("----------------------------------------------------------------");
            out.println("출고 처리가 완료되었습니다.");
            out.println();
            out.printf("  주문번호  %s%n", released.getOrderId());
            out.printf("  출고수량  %d ea%n", released.getQuantity());
            out.printf("  처리일시  %s%n", released.getReleasedAt().format(DT_FMT));
            out.printf("  상태      CONFIRMED → " + GREEN + "[RELEASE]" + RESET + "%n");
        } catch (IllegalStateException e) {
            out.println("오류: " + e.getMessage());
        }
        out.println("================================================================");
    }
}
