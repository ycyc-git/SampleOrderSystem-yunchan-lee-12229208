package org.example.controller;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.StockStatusDto;
import org.example.service.MonitoringService;
import org.example.util.ConsoleReader;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class MonitoringController {

    private static final String RESET   = "[0m";
    private static final String BLUE    = "[94m";
    private static final String GREEN   = "[92m";
    private static final String YELLOW  = "[93m";
    private static final String MAGENTA = "[95m";
    private static final String RED     = "[91m";

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MonitoringService service;
    private final ConsoleReader reader;
    private final PrintStream out;

    public MonitoringController(MonitoringService service,
                                ConsoleReader reader, PrintStream out) {
        this.service = service;
        this.reader = reader;
        this.out = out;
    }

    public void run() {
        while (true) {
            out.println("================================================================");
            out.println("[4] 모니터링  " + LocalDateTime.now().format(DT_FMT));
            out.println("----------------------------------------------------------------");
            out.println("[1] 주문량 확인    [2] 재고량 확인    [0] 뒤로");

            String choice = reader.readLine("선택 > ");
            switch (choice) {
                case "0" -> { return; }
                case "1" -> {
                    showOrderSummary();
                    showStockStatus();
                }
                case "2" -> showStockStatus();
                default  -> out.println("잘못된 입력입니다. 다시 선택하세요.");
            }
        }
    }

    private void showOrderSummary() {
        Map<OrderStatus, Long> summary = service.getOrderSummaryByStatus();
        out.println("상태별 주문 현황");
        out.println();
        printStatusSection(BLUE,   OrderStatus.RESERVED,  summary, "");
        printStatusSection(GREEN,  OrderStatus.CONFIRMED,  summary, "");
        printStatusSection(YELLOW, OrderStatus.PRODUCING,  summary, "  ← 생산라인 대기");
        printOrderLine(MAGENTA, OrderStatus.RELEASE, summary, "");
        out.println();
        out.println("----------------------------------------------------------------");
    }

    private void printStatusSection(String color, OrderStatus status,
                                    Map<OrderStatus, Long> summary, String note) {
        printOrderLine(color, status, summary, note);
        List<Order> orders = service.getOrdersByStatus(status);
        if (!orders.isEmpty()) {
            out.printf("  %-4s %-20s %-14s %-20s %s%n",
                    "번호", "주문번호", "고객", "시료", "수량");
            out.println("  ---- -------------------- -------------- -------------------- ----------");
            for (int i = 0; i < orders.size(); i++) {
                Order o = orders.get(i);
                out.printf("  %-4d %-20s %-14s %-20s %d ea%n",
                        i + 1,
                        o.getOrderId(),
                        o.getCustomerName(),
                        o.getSample().getName(),
                        o.getQuantity());
            }
        }
        out.println();
    }

    private void printOrderLine(String color, OrderStatus status,
                                Map<OrderStatus, Long> summary, String note) {
        long count = summary.getOrDefault(status, 0L);
        String text = "[" + status.name() + "]";
        int padLen = 13 - text.length();
        String pad = " ".repeat(Math.max(1, padLen));
        out.printf("%s%s%s%s%d건%s%n", color, text, RESET, pad, count, note);
    }

    private void showStockStatus() {
        List<StockStatusDto> list = service.getStockStatus();
        out.println("재고 현황");
        out.println();
        out.printf("%-24s %-11s %-10s %s%n", "시료명", "재고", "상태", "잔여율");
        out.println("------------------------ ----------- ---------- -------------------");

        if (list.isEmpty()) {
            out.println("등록된 시료가 없습니다.");
        } else {
            for (StockStatusDto dto : list) {
                String labelColor = switch (dto.getStockLabel()) {
                    case "여유" -> GREEN;
                    case "부족" -> YELLOW;
                    default     -> RED;
                };
                String labelBadge = labelColor + "[" + dto.getStockLabel() + "]" + RESET;
                String bar = progressBar(dto.getRemainingRate());
                out.printf("%-24s %-11s %-10s %s%n",
                        dto.getSample().getName(),
                        dto.getStock() + " ea",
                        labelBadge,
                        bar);
            }
        }
        out.println();
    }

    private String progressBar(int rate) {
        int filled = rate / 10;
        return "█".repeat(filled) + "░".repeat(10 - filled) + "  " + rate + "%";
    }
}
