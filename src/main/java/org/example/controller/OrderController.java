package org.example.controller;

import org.example.domain.Order;
import org.example.domain.Sample;
import org.example.service.OrderService;
import org.example.util.ConsoleReader;

import java.io.PrintStream;
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

        Sample sample;
        while (true) {
            String sampleId = reader.readLine("시료 ID     > ");
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
        out.println("예약 접수 완료.");
        out.println();
        out.printf("주문번호    %s%n", order.getOrderId());
        out.printf("현재 상태   [%s]%n", order.getStatus().name());
        out.println();
        out.println("※ 재고 확인은 [3] 승인 메뉴에서 직접 진행하세요.");
        out.println();
    }
}
