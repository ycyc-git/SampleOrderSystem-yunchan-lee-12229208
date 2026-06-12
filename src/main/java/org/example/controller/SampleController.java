package org.example.controller;

import org.example.domain.Sample;
import org.example.service.SampleService;
import org.example.util.ConsoleReader;

import java.io.PrintStream;
import java.util.List;

public class SampleController {

    private static final int PAGE_SIZE = 5;
    private static final String RESET = "\033[0m";
    private static final String RED   = "\033[91m";

    private final SampleService service;
    private final ConsoleReader reader;
    private final PrintStream out;

    public SampleController(SampleService service, ConsoleReader reader, PrintStream out) {
        this.service = service;
        this.reader = reader;
        this.out = out;
    }

    public void run() {
        while (true) {
            printMenu();
            String input = reader.readLine("선택 > ");
            switch (input) {
                case "1": registerSample(); break;
                case "2": listSamples(); break;
                case "3": searchSamples(); break;
                case "0": return;
                default:  out.println("잘못된 입력입니다. 다시 선택하세요.");
            }
        }
    }

    private void printMenu() {
        out.println("================================================================");
        out.println("[1] 시료 관리");
        out.println("----------------------------------------------------------------");
        out.println("[1] 시료 등록    [2] 시료 목록    [3] 시료 검색    [0] 뒤로");
    }

    // ── 시료 등록 ─────────────────────────────────────────────────

    private void registerSample() {
        out.println("----------------------------------------------------------------");

        String id;
        while (true) {
            id = reader.readLine("시료 ID        > ");
            if (id.isBlank()) {
                out.println("시료 ID를 입력하세요.");
            } else if (service.findById(id).isPresent()) {
                out.println("이미 등록된 시료 ID입니다.");
            } else {
                break;
            }
        }

        String name;
        while (true) {
            name = reader.readLine("시료명         > ");
            if (!name.isBlank()) break;
            out.println("시료명을 입력하세요.");
        }

        double avgTime;
        while (true) {
            avgTime = reader.readDouble("평균 생산시간(min/ea) > ");
            if (avgTime > 0) break;
            out.println("평균 생산시간은 0보다 커야 합니다.");
        }

        double yield;
        while (true) {
            yield = reader.readDouble("수율 (0~1)     > ");
            if (yield > 0 && yield <= 1.0) break;
            out.println("수율은 0 초과 1 이하의 값을 입력하세요.");
        }

        int stock;
        while (true) {
            stock = reader.readInt("초기 재고 (ea) > ");
            if (stock >= 0) break;
            out.println("초기 재고는 0 이상이어야 합니다.");
        }

        Sample sample = service.register(id, name, avgTime, yield, stock);
        out.println("시료가 등록되었습니다.");
        out.printf("ID: %s  이름: %s  생산시간: %.1f min/ea  수율: %.2f%n",
                sample.getId(), sample.getName(),
                sample.getAvgProductionTime(), sample.getYield());
        out.println();
    }

    // ── 시료 목록 ─────────────────────────────────────────────────

    private void listSamples() {
        List<Sample> all = service.getAll();
        if (all.isEmpty()) {
            out.println("등록된 시료가 없습니다.");
            return;
        }

        int page = 0;
        int totalPages = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;

        while (true) {
            printSamplePage(all, page, totalPages);
            String input = reader.readLine("선택 > ");
            if ("N".equalsIgnoreCase(input) && page < totalPages - 1) {
                page++;
            } else if ("P".equalsIgnoreCase(input) && page > 0) {
                page--;
            } else {
                return;
            }
        }
    }

    private void printSamplePage(List<Sample> all, int page, int totalPages) {
        out.printf("등록 시료 목록  (총 %d종)%n%n", all.size());
        printTableHeader();

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, all.size());
        for (int i = from; i < to; i++) {
            printTableRow(all.get(i));
        }
        out.println();

        int remaining = all.size() - to;
        if (remaining > 0) {
            out.printf("...외 %d종    [N] 다음페이지%n", remaining);
        }
        if (page > 0) {
            out.println("[P] 이전페이지");
        }
    }

    // ── 시료 검색 ─────────────────────────────────────────────────

    private void searchSamples() {
        while (true) {
            printSearchMenu();
            String input = reader.readLine("선택 > ");
            switch (input) {
                case "1": searchById(); break;
                case "2": searchByName(); break;
                case "3": searchByYield(); break;
                case "4": searchByStock(); break;
                case "0": return;
                default: out.println("잘못된 입력입니다. 다시 선택하세요.");
            }
        }
    }

    private void printSearchMenu() {
        out.println("================================================================");
        out.println("[1] 시료 검색");
        out.println("----------------------------------------------------------------");
        out.println("[1] ID 검색       [2] 시료명 검색");
        out.println("[3] 수율 검색     [4] 재고 검색     [0] 뒤로");
    }

    private void searchById() {
        String keyword = reader.readLine("시료 ID (부분 일치) > ");
        printSearchResults(service.searchById(keyword), "검색어: " + keyword);
    }

    private void searchByName() {
        String keyword = reader.readLine("시료명 (부분 일치) > ");
        printSearchResults(service.searchByName(keyword), "검색어: " + keyword);
    }

    private void searchByYield() {
        double minYield = reader.readDouble("최소 수율 (0~1) > ");
        printSearchResults(service.searchByYield(minYield),
                String.format("수율 %.2f 이상", minYield));
    }

    private void searchByStock() {
        int minStock = reader.readInt("최소 재고 (ea) > ");
        printSearchResults(service.searchByStock(minStock),
                String.format("재고 %d ea 이상", minStock));
    }

    private void printSearchResults(List<Sample> results, String criteria) {
        if (results.isEmpty()) {
            out.printf("검색 결과가 없습니다. (%s)%n", criteria);
        } else {
            out.printf("검색 결과  (%d종)  %s%n%n", results.size(), criteria);
            printTableHeader();
            for (Sample s : results) {
                printTableRow(s);
            }
            out.println();
        }
    }

    // ── 공통 테이블 출력 ──────────────────────────────────────────

    private void printTableHeader() {
        out.printf("%-12s %-20s %-14s %-8s %s%n",
                "ID", "시료명", "평균 생산시간", "수율", "현재 재고");
        out.println("------------ -------------------- -------------- -------- ----------");
    }

    private void printTableRow(Sample s) {
        String stockStr = s.getStock() == 0
                ? RED + "0 ea" + RESET
                : s.getStock() + " ea";
        out.printf("%-12s %-20s %-14s %-8s %s%n",
                s.getId(),
                s.getName(),
                String.format("%.1f min/ea", s.getAvgProductionTime()),
                String.format("%.2f", s.getYield()),
                stockStr);
    }
}
