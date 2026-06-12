package org.example.controller;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.Sample;
import org.example.service.OrderService;
import org.example.util.Ansi;
import org.example.util.ConsoleReader;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

public class OrderController {

    private final OrderService service;
    private final ConsoleReader reader;
    private final PrintStream out;

    public OrderController(OrderService service, ConsoleReader reader, PrintStream out) {
        this.service = service;
        this.reader = reader;
        this.out = out;
    }

    public void run() {
        // ── Step 1: 입력 ──────────────────────────────────────────
        out.println("================================================================");
        out.println("[2] 시료 주문");
        out.println("----------------------------------------------------------------");

        // 시료 목록 표시
        List<Sample> allSamples = service.getAllSamples();
        if (allSamples.isEmpty()) {
            out.println("등록된 시료가 없습니다. 먼저 [1] 시료 등록을 진행하세요.");
            return;
        }
        out.println("등록된 시료 목록");
        out.println();
        out.printf("%-10s %-26s %s%n", "ID", "시료명", "재고");
        out.println("---------- -------------------------- ----------");
        for (Sample s : allSamples) {
            out.printf("%-10s %-26s %d ea%n",
                    s.getId(), s.getName(), s.getStock());
        }
        out.println();
        out.println("※ 시료 ID를 입력하세요. 빈 칸으로 엔터 시 뒤로 돌아갑니다.");
        out.println("----------------------------------------------------------------");

        Sample sample;
        while (true) {
            String sampleId = reader.readLine("시료 ID     > ");
            if (sampleId.isBlank()) {
                out.println("주문이 취소되었습니다.");
                return;
            }
            Optional<Sample> opt = service.findSampleById(sampleId);
            if (opt.isPresent()) {
                sample = opt.get();
                break;
            }
            out.println("등록되지 않은 시료 ID입니다.");
        }

        String customerName;
        while (true) {
            customerName = reader.readLine("고객명      > ");
            if (!customerName.isBlank()) break;
            out.println("고객명을 입력하세요.");
        }

        int quantity;
        while (true) {
            quantity = reader.readInt("수량 (ea)   > ");
            if (quantity > 0) break;
            out.println("주문 수량은 1 이상이어야 합니다.");
        }

        // ── Step 2: 확인 ──────────────────────────────────────────
        out.println("----------------------------------------------------------------");
        out.println("입력 내용 확인");
        out.printf("시료      %s  (%s)%n", sample.getName(), sample.getId());
        out.printf("고객      %s%n", customerName);
        out.printf("수량      %d ea%n%n", quantity);
        out.println("[Y] 예약 접수    [N] 취소");

        String choice;
        while (true) {
            choice = reader.readLine("선택 > ");
            if ("Y".equalsIgnoreCase(choice) || "N".equalsIgnoreCase(choice)) break;
            out.println("Y 또는 N을 입력하세요.");
        }

        if ("N".equalsIgnoreCase(choice)) {
            out.println("주문이 취소되었습니다.");
            return;
        }

        // ── Step 3: 완료 ──────────────────────────────────────────
        Order order = service.reserve(sample.getId(), customerName, quantity);
        out.println("----------------------------------------------------------------");
        out.println(Ansi.GREEN + "예약 접수 완료." + Ansi.RESET);
        out.println();
        out.printf("주문번호    %s%n", order.getOrderId());
        out.printf("현재 상태   %s[%s]%s%n", Ansi.BLUE, order.getStatus().name(), Ansi.RESET);
        out.println();
        out.println("※ 재고 확인은 [3] 승인 메뉴에서 직접 진행하세요.");
        out.println();
    }

    public void approveOrReject() {
        out.println("================================================================");
        out.println("[3] 주문 승인/거절");
        out.println("----------------------------------------------------------------");

        List<Order> reserved = service.getReservedOrders();
        if (reserved.isEmpty()) {
            out.println("승인 대기 중인 주문이 없습니다.");
            return;
        }

        // ── 목록 표시 ─────────────────────────────────────────────
        out.println("승인 대기 중인 예약 목록  (RESERVED)");
        out.println();
        out.printf("%-8s %-20s %-20s %-22s %-12s %s%n",
                "번호", "주문번호", "고객", "시료", "수량", "상태");
        out.println("-------- -------------------- -------------------- ---------------------- ------------ ----------");
        for (int i = 0; i < reserved.size(); i++) {
            Order o = reserved.get(i);
            out.printf("[%-6d] %-20s %-20s %-22s %-12s %s[%s]%s%n",
                    i + 1, o.getOrderId(), o.getCustomerName(),
                    o.getSample().getName(), o.getQuantity() + " ea",
                    Ansi.BLUE, o.getStatus().name(), Ansi.RESET);
        }
        out.println();

        // ── 번호 선택 ─────────────────────────────────────────────
        int selectedIdx;
        while (true) {
            int num = reader.readInt("승인할 번호 > ");
            if (num >= 1 && num <= reserved.size()) {
                selectedIdx = num - 1;
                break;
            }
            out.println("유효하지 않은 번호입니다.");
        }

        Order selected = reserved.get(selectedIdx);

        // ── 재고 분석 ─────────────────────────────────────────────
        OrderService.StockAnalysisResult analysis =
                service.analyzeStock(selected.getOrderId());
        out.println("재고 확인 중...");
        out.println();
        out.printf("%-16s %-22s 현재 재고   %d ea%n",
                "시료", selected.getSample().getName(), analysis.currentStock);
        out.printf("%-16s %d ea%-18s 부족분      %d ea%n",
                "주문 수량", analysis.quantity, "", analysis.shortage);
        out.println("----------------------------------------------------------------");

        if (analysis.shortage == 0) {
            out.println("재고 충분. 즉시 출고 대기로 전환합니다.");
        } else {
            out.printf("재고 부족. 부족분 %d ea 승인하시겠습니까?  (실생산량 %d ea / %.1f min)%n",
                    analysis.shortage, analysis.actualQty, analysis.totalTime);
        }
        out.println();
        out.println("[Y] 승인    [N] 주문 거절");

        String choice;
        while (true) {
            choice = reader.readLine("선택 > ");
            if ("Y".equalsIgnoreCase(choice) || "N".equalsIgnoreCase(choice)) break;
            out.println("Y 또는 N을 입력하세요.");
        }

        // ── 결과 ──────────────────────────────────────────────────
        out.println("----------------------------------------------------------------");
        if ("Y".equalsIgnoreCase(choice)) {
            Order updated = service.approve(selected.getOrderId());
            String statusColor = updated.getStatus() == OrderStatus.CONFIRMED
                    ? Ansi.GREEN : Ansi.YELLOW;
            out.printf("상태 변경  %s[RESERVED]%s → %s[%s]%s%n",
                    Ansi.BLUE, Ansi.RESET, statusColor, updated.getStatus().name(), Ansi.RESET);
        } else {
            service.reject(selected.getOrderId());
            out.printf("상태 변경  %s[RESERVED]%s → %s[REJECTED]%s%n",
                    Ansi.BLUE, Ansi.RESET, Ansi.RED, Ansi.RESET);
            out.println("주문이 거절되었습니다.");
        }
        out.printf("주문번호     %s%n", selected.getOrderId());
        out.println();
    }
}
