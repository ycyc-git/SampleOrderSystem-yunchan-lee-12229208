# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Uses Gradle wrapper (JDK 17, Gradle 9.3.0).

```bat
# Build
gradlew.bat build

# Test (JUnit 5)
gradlew.bat test

# Run a single test class
gradlew.bat test --tests "org.example.SomeTest"

# Clean
gradlew.bat clean

# Run the main class after building
java -cp build/classes/java/main org.example.Main
```

## Architecture

Single-module Java project with standard Maven layout:

- `src/main/java/org/example/` — application source (currently only `Main.java`)
- `src/test/java/` — JUnit 5 tests (currently empty)
- Group/artifact: `org.example:SampleOrderSystem:1.0-SNAPSHOT`

No external runtime dependencies; JUnit 5 (BOM 6.0.0) is test-only.

---

## Product & Design Docs

This project implements the **반도체 시료 생산주문관리 시스템** (S-Semi semiconductor sample order management system).

- **PRD**: [`docs/PRD.md`](docs/PRD.md) — full product requirements (domain terms, order state machine, feature specs)

### Implementation Phases

각 Phase는 독립적으로 실행·수동 테스트 가능하다.

| Phase | 문서 | 핵심 내용 | 테스트 가능 기능 |
|-------|------|-----------|-----------------|
| 01 | [phase01-main-shell.md](docs/design/phase01-main-shell.md) | `ConsoleReader`, `AppContext`, `MainMenu` 껍데기 | 메인 화면 출력, 메뉴 선택, 종료 |
| 02 | [phase02-sample-register-list.md](docs/design/phase02-sample-register-list.md) | `Sample`, `SampleRepository`, `SampleService`, `SampleController` | 시료 등록·목록·페이지네이션, 메인 현황 반영 |
| 03 | [phase03-sample-search.md](docs/design/phase03-sample-search.md) | `SampleService.search()` | 이름 부분 일치 검색 |
| 04 | [phase04-order-reserve.md](docs/design/phase04-order-reserve.md) | `Order(RESERVED)`, `OrderRepository`, `OrderService.reserve()` | 주문 접수, 주문번호 생성, 전체 주문 현황 반영 |
| 05 | [phase05-order-approve-reject.md](docs/design/phase05-order-approve-reject.md) | `OrderService.approve()/reject()`, 재고 분석 | 승인(CONFIRMED/PRODUCING), 거절(REJECTED) |
| 06 | [phase06-monitoring.md](docs/design/phase06-monitoring.md) | `MonitoringService`, `StockStatusDto`, `MonitoringController` | 상태별 주문 수, 재고 현황·잔여율 바 |
| 07 | [phase07-production-queue.md](docs/design/phase07-production-queue.md) | `ProductionJob`, `ProductionLineService.enqueue()`, `ProductionLineController` | 생산 큐 등록·조회 (정적) |
| 08 | [phase08-production-tick.md](docs/design/phase08-production-tick.md) | `ProductionLineService.tick()`, `ScheduledExecutorService` | 생산 자동 완료 → CONFIRMED 전환, 진행률 갱신 |
| 09 | [phase09-release.md](docs/design/phase09-release.md) | `ReleaseService`, `ReleaseController` | 출고 처리 → RELEASE 전환 |
| 10 | [phase10-integration.md](docs/design/phase10-integration.md) | 전체 연동·컬러·엣지케이스 완성 | 전체 시나리오 통합 테스트 |

### Detailed Design Docs

| 문서 | 내용 |
|------|------|
| [screen-specs.md](docs/design/screen-specs.md) | 화면별 레이아웃 · 상태 · 입력 유효성 · 에러 메시지 · 엣지 케이스 |
| [data-flows.md](docs/design/data-flows.md) | DF-01~10 레이어별 단계 처리, 오류 경로, 사후 상태, 상태 전이 제약 테이블 |

### Key Domain Rules
- **실 생산량**: `ceil(부족분 / (수율 × 0.9))`
- **재고 레이블**: 고갈(stock=0) / 부족(stock < pendingDemand) / 여유(stock ≥ pendingDemand)
- **생산 큐 전략**: FIFO — `ProductionLineService` 내부 `Queue<ProductionJob>`
- **상태 전이**: `RESERVED → CONFIRMED | PRODUCING → CONFIRMED → RELEASE` (REJECTED는 모니터링 제외)
