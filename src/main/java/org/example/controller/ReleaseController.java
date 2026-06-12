package org.example.controller;

import org.example.domain.Order;
import org.example.service.ReleaseService;
import org.example.util.ConsoleReader;

import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReleaseController {

    private static final String RESET   = "\033[0m";
    private static final String GREEN   = "\033[92m";
    private static final String YELLOW  = "\033[93m";
    private static final String MAGENTA = "\033[95m";
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

        out.println("출고 가능 주문  (CONFIRMED)");
        out.println();
        out.printf("%-7s %-14s %-15s %-20s %s%n",
                "번호", "주문번호", "고객", "시료", "수량");
        out.println("------- -------------- --------------- -------------------- -----------");
        for (int i = 0; i < confirmed.size(); i++) {
            Order o = confirmed.get(i);
            out.printf("[%-5d] %-14s %-15s %-20s %d ea%n",
                    i + 1,
                    o.getOrderId(),
                    o.getCustomerName(),
                    o.getSample().getName(),
                    o.getQuantity());
        }
        out.println("----------------------------------------------------------------");

        while (true) {
            String input = reader.readLine("출고할 번호 > ").trim();

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
                continue;
            }

            if (idx < 0 || idx >= confirmed.size()) {
                out.println("유효하지 않은 번호입니다.");
                continue;
            }

            Order target = confirmed.get(idx);
            try {
                Order released = releaseService.release(target.getOrderId());
                out.println("----------------------------------------------------------------");
                out.println(GREEN + "출고 처리 완료." + RESET);
                out.println();
                out.printf("  주문번호  %s%n", released.getOrderId());
                out.printf("  출고수량  %d ea%n", released.getQuantity());
                out.printf("  처리일시  %s%n", released.getReleasedAt().format(DT_FMT));
                out.printf("  상태      CONFIRMED → %s[RELEASE]%s%n", MAGENTA, RESET);
            } catch (IllegalStateException e) {
                out.println("오류: " + e.getMessage());
                out.println();
                out.println("생산 큐에 다시 등록하시겠습니까?");
                out.println("[Y] 생산 큐 등록    [N] 취소");

                String requeueChoice;
                while (true) {
                    requeueChoice = reader.readLine("선택 > ");
                    if ("Y".equalsIgnoreCase(requeueChoice)
                            || "N".equalsIgnoreCase(requeueChoice)) break;
                    out.println("Y 또는 N을 입력하세요.");
                }

                if ("Y".equalsIgnoreCase(requeueChoice)) {
                    Order requeued = releaseService.requeueToProducing(target.getOrderId());
                    out.println("----------------------------------------------------------------");
                    out.printf("상태 변경  CONFIRMED → %s[PRODUCING]%s%n", YELLOW, RESET);
                    out.printf("주문번호   %s%n", requeued.getOrderId());
                    out.println("생산 큐에 등록되었습니다.");
                } else {
                    out.println("재생산 큐 등록을 취소했습니다.");
                }
            }
            out.println("================================================================");
            return;
        }
    }
}
