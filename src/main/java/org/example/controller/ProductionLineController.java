package org.example.controller;

import org.example.domain.ProductionJob;
import org.example.service.ProductionLineService;
import org.example.util.ConsoleReader;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class ProductionLineController {

    private static final String RESET  = "\033[0m";
    private static final String YELLOW = "\033[93m";
    private static final String GRAY   = "\033[90m";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ProductionLineService service;
    private final ConsoleReader reader;
    private final PrintStream out;

    public ProductionLineController(ProductionLineService service,
                                    ConsoleReader reader, PrintStream out) {
        this.service = service;
        this.reader = reader;
        this.out = out;
    }

    public void run() {
        out.println("================================================================");
        out.println("[5] 생산라인 조회  FIFO 방식");
        out.println("----------------------------------------------------------------");

        Optional<ProductionJob> current = service.getCurrentJob();

        if (current.isEmpty()) {
            out.println("생산라인  1개  (단일 라인)    현재 상태: " +
                    GRAY + "[IDLE]" + RESET);
            out.println();
            out.println("현재 생산 중인 작업이 없습니다.");
            out.println();
            out.println("대기 중인 주문도 없습니다.");
        } else {
            out.println("생산라인  1개  (단일 라인)    현재 상태: " +
                    YELLOW + "[RUNNING]" + RESET);
            out.println();
            printCurrentJob(current.get());
            out.println("----------------------------------------------------------------");
            printWaitingQueue(current.get());
        }

        out.println();
        out.println("* 부족분 = 주문량 - 재고,  실생산량 = ceil(부족분 / (수율 * 0.9))");
        out.println("* 선입선출(FIFO) 방식으로 처리됩니다.");
        reader.readLine("선택 > ");
    }

    private void printCurrentJob(ProductionJob job) {
        out.println("현재 처리 중");
        out.println();

        String sampleName = job.getOrder().getSample().getName();
        String orderId = job.getOrder().getOrderId();
        int orderQty = job.getOrder().getQuantity();
        int stock = job.getOrder().getSample().getStock();
        double yield = job.getOrder().getSample().getYield();
        double avgTime = job.getOrder().getSample().getAvgProductionTime();

        out.printf("  주문번호   %-22s 시료   %s%n", "[" + orderId + "]", sampleName);
        out.printf("  주문량     %d ea  재고 %d ea  →  부족 %d ea  →  실생산량 %d ea  (수율 %.2f / %.1f min)%n",
                orderQty, stock, job.getShortage(),
                job.getActualProductionQty(), yield, avgTime);

        int progress = service.getProgressPercent();
        int filled = progress / 10;
        String bar = "[" + "█".repeat(filled) + "░".repeat(10 - filled) + "]";
        String estimatedFinish = service.getEstimatedFinishTime();
        out.printf("  진행       %s  %d%%    완료 예정  %s%n", bar, progress, estimatedFinish);
        out.println();
    }

    private void printWaitingQueue(ProductionJob current) {
        List<ProductionJob> waiting = service.getWaitingQueue();
        out.println("대기 중인 주문  (FIFO 순)");
        out.println();

        if (waiting.isEmpty()) {
            out.println("대기 중인 주문이 없습니다.");
            return;
        }

        out.printf("%-7s %-14s %-17s %-11s %-11s %-11s %-9s%n",
                "순서", "주문번호", "시료", "주문량", "부족분", "실생산량", "예상 완료");
        out.println("------- -------------- ----------------- ----------- ----------- ----------- ---------");

        long msPerMinute = service.getMsPerMinute();
        LocalDateTime runningFinish = LocalDateTime.now()
                .plusNanos(service.getRemainingMs() * 1_000_000L);

        for (int i = 0; i < waiting.size(); i++) {
            ProductionJob job = waiting.get(i);
            long jobMs = (long)(job.getTotalProductionTime() * msPerMinute);
            LocalDateTime jobFinish = runningFinish.plusNanos(jobMs * 1_000_000L);
            out.printf("%-7d %-14s %-17s %-11s %-11s %-11s %-9s%n",
                    i + 1,
                    job.getOrder().getOrderId(),
                    job.getOrder().getSample().getName(),
                    job.getOrder().getQuantity() + " ea",
                    job.getShortage() + " ea",
                    job.getActualProductionQty() + " ea",
                    jobFinish.format(TIME_FMT));
            runningFinish = jobFinish;
        }
    }

}
