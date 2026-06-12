# CLAUDE.md

이 파일은 이 저장소에서 작업할 때 Claude Code(claude.ai/code)에게 제공하는 안내 문서입니다.

## 빌드 및 실행

Gradle 래퍼 사용 (JDK 17, Gradle 9.3.0).

```bat
# 빌드
gradlew.bat build

# 테스트 (JUnit 5)
gradlew.bat test

# 단일 테스트 클래스 실행
gradlew.bat test --tests "org.example.SomeTest"

# 클린
gradlew.bat clean

# 빌드 후 메인 클래스 실행
gradlew.bat run
```

## 아키텍처

표준 Maven 레이아웃의 단일 모듈 Java 프로젝트:

- `src/main/java/org/example/` — 애플리케이션 소스 (domain, service, controller, repository, util 패키지 구성)
- `src/test/java/` — JUnit 5 테스트 (각 서비스·컨트롤러별 단위 테스트 포함)
- Group/artifact: `org.example:SampleOrderSystem:1.0-SNAPSHOT`

런타임 의존성: **Gson 2.11.0** (JSON 영속성). JUnit 5 (BOM 6.0.0)는 테스트 전용.

---

## 제품 및 설계 문서

이 프로젝트는 **반도체 시료 생산주문관리 시스템** (S-Semi)을 구현합니다.

- **PRD**: [`docs/PRD.md`](docs/PRD.md) — 전체 제품 요구사항 (도메인 용어, 주문 상태 머신, 기능 명세)

### 구현 단계 (Phase)

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

### 상세 설계 문서

| 문서 | 내용 |
|------|------|
| [screen-specs.md](docs/design/screen-specs.md) | 화면별 레이아웃 · 상태 · 입력 유효성 · 에러 메시지 · 엣지 케이스 |
| [data-flows.md](docs/design/data-flows.md) | DF-01~10 레이어별 단계 처리, 오류 경로, 사후 상태, 상태 전이 제약 테이블 |
| [data-persistence.md](docs/design/data-persistence.md) | Gson 기반 JSON 영속성 전략: 파일 위치, DTO 패턴, 참조 복원 순서, 테스트 전략 |

### 핵심 도메인 규칙
- **실 생산량**: `ceil(부족분 / (수율 × 0.9))`
- **재고 레이블**: 고갈(stock=0) / 부족(stock < pendingDemand) / 여유(stock ≥ pendingDemand)
- **재고 모델**: `stock`(가용 — 신규 주문 계산 기준) + `reservedStock`(예약 — 승인된 주문 할당분, 출고 시 차감)
- **pendingDemand**: RESERVED 주문 수량 합계만 포함 (CONFIRMED/PRODUCING은 이미 reservedStock으로 이동됨)
- **생산 큐 전략**: FIFO — `ProductionLineService` 내부 `Queue<ProductionJob>`
- **상태 전이**: `RESERVED → CONFIRMED | PRODUCING → CONFIRMED → RELEASE` / `CONFIRMED → PRODUCING` (재생산 시) / `RESERVED → REJECTED`
