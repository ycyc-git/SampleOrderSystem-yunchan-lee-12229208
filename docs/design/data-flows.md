# Data Flows — 데이터 플로우 상세

## 레이어 구조

```
[사용자 입력]
     │  ConsoleReader.readLine() / readInt() / readDouble()
     ▼
[Controller]  — 입출력 조율, 화면 렌더링
     │  DTO 또는 원시값 전달
     ▼
[Service]     — 비즈니스 규칙, 유효성 검증, 상태 전이
     │  도메인 객체 직접 참조
     ▼
[Repository]  — 인메모리 컬렉션 CRUD
     │
     ▼
[Domain]      — Sample / Order / ProductionJob (상태 전이 규칙 포함)
```

---

## DF-01 — 시료 등록

**트리거**: SCR-1-1에서 수율까지 입력 완료 후 유효성 통과

```
Controller
  1. ConsoleReader로 id, name, avgTime, yield 수집
  2. 필드별 null/empty 1차 검증 → 실패 시 해당 필드 재입력
  3. SampleService.register(id, name, avgTime, yield) 호출

Service: SampleService.register()
  4. SampleRepository.findById(id) → 존재하면 throw DuplicateIdException
  5. 0.0 < yield ≤ 1.0 검증 → 실패 시 throw InvalidYieldException
  6. avgTime > 0 검증 → 실패 시 throw InvalidAvgTimeException
  7. new Sample(id, name, avgTime, yield, stock=0) 생성
  8. SampleRepository.add(sample)

Repository: SampleRepository.add()
  9. Map<String, Sample> 에 put(id, sample)

Controller
  10. 성공 메시지 출력 (ID, 이름, 생산시간, 수율)
```

**오류 경로**

| 예외 | 발생 지점 | Controller 처리 |
|------|-----------|-----------------|
| DuplicateIdException | step 4 | "이미 등록된 시료 ID입니다." → ID 재입력 |
| InvalidYieldException | step 5 | "수율은 0 초과 1 이하여야 합니다." → 수율 재입력 |
| InvalidAvgTimeException | step 6 | "평균 생산시간은 0보다 커야 합니다." → 재입력 |

**사후 상태**: `SampleRepository`에 신규 Sample 추가, stock=0

---

## DF-02 — 시료 목록 조회

**트리거**: SCR-1에서 `[2]` 선택

```
Controller
  1. SampleService.getAll() 호출

Service: SampleService.getAll()
  2. SampleRepository.findAll() → List<Sample> 반환 (등록 순서 유지)

Controller
  3. 5개 단위로 페이지 슬라이싱
  4. 현재 페이지 렌더링
  5. [N]/[P] 입력 → 페이지 이동, 기타 입력 → 서브메뉴 복귀
```

**사후 상태**: 변경 없음 (조회 전용)

---

## DF-03 — 시료 검색

**트리거**: SCR-1에서 `[3]` 선택

```
Controller
  1. 검색어 입력 수신

Service: SampleService.search(keyword)
  2. SampleRepository.findByName(keyword) 호출

Repository: SampleRepository.findByName()
  3. Map.values() 전체 순회
  4. sample.name.toLowerCase().contains(keyword.toLowerCase()) 필터
  5. 결과 List<Sample> 반환

Controller
  6. 결과 > 0 → 목록 형식 출력
  7. 결과 = 0 → "검색 결과가 없습니다. (검색어: [keyword])"
```

**사후 상태**: 변경 없음

---

## DF-04 — 주문 접수 (RESERVED 생성)

**트리거**: SCR-2에서 입력 확인 후 `[Y]` 선택

```
Controller
  1. sampleId, customerName, quantity 입력 수신
  2. SampleService.findById(sampleId) → 없으면 오류 메시지 후 sampleId 재입력
  3. 확인 화면 렌더링 (시료명, 고객명, 수량)
  4. [Y]/[N] 입력 → N이면 복귀

Service: OrderService.reserve(sampleId, customerName, quantity)
  5. SampleRepository.findById(sampleId) → Optional이 empty면 throw SampleNotFoundException
  6. quantity ≤ 0 이면 throw InvalidQuantityException
  7. orderId = generateOrderId()  // "ORD-" + LocalDate.now() + "-" + 4자리 일련번호
  8. new Order(orderId, sample, customerName, quantity, RESERVED, createdAt=now())
  9. OrderRepository.save(order)

Controller
  10. 주문번호, [RESERVED] 배지, 안내 메시지 출력
```

**orderId 생성 규칙**
```
날짜 부분: LocalDate.now().format("yyyyMMdd")
일련번호 : 오늘 날짜 주문 수 + 1, 4자리 zero-padding
예) ORD-20260416-0043
```

**오류 경로**

| 예외 | 처리 |
|------|------|
| SampleNotFoundException | "등록되지 않은 시료 ID입니다." → sampleId 재입력 |
| InvalidQuantityException | "주문 수량은 1 이상이어야 합니다." → 재입력 |

**사후 상태**: OrderRepository에 신규 Order(RESERVED) 추가

---

## DF-05 — 주문 승인 (재고 충분 → CONFIRMED)

**트리거**: SCR-3 번호 선택 → 재고 분석 → `[Y]` 선택

```
Controller
  1. OrderService.getReservedOrders() → 목록 렌더링
  2. 번호 입력 → 해당 Order 조회
  3. 재고 분석 계산 및 표시:
       현재 재고  = order.sample.stock
       부족분     = max(0, order.quantity - 현재 재고)  →  0
       → "재고 충분" 메시지
  4. [Y] 입력

Service: OrderService.approve(orderId)
  5. OrderRepository.findById(orderId) → Order
  6. order.status != RESERVED 이면 throw InvalidOrderStateException
  7. sample = order.sample
  8. shortage = max(0, order.quantity - sample.stock)
  9. shortage == 0 이므로:
       sample.stock -= order.quantity          // 재고 차감
       order.transition(CONFIRMED)             // 상태 전이
       OrderRepository.save(order)

Controller
  10. "상태 변경  RESERVED → [CONFIRMED]" + 주문번호 출력
```

**사후 상태**
- `sample.stock` 감소 (order.quantity 만큼)
- `order.status` = CONFIRMED

---

## DF-06 — 주문 승인 (재고 부족 → PRODUCING)

**트리거**: SCR-3 번호 선택 → 재고 부족 분석 → `[Y]` 선택

```
Controller
  1. (DF-05 step 1~2 동일)
  3. 재고 분석 계산 및 표시:
       shortage = order.quantity - sample.stock  >  0
       actualQty  = ceil(shortage / (sample.yield * 0.9))
       totalTime  = sample.avgProductionTime * actualQty   (단위: min)
       → "재고 부족. 부족분 N ea 승인하시겠습니까? (실생산량 N ea / N min)"
  4. [Y] 입력

Service: OrderService.approve(orderId)
  5~8. (DF-05 동일)
  9. shortage > 0 이므로:
       order.transition(PRODUCING)
       OrderRepository.save(order)
       ProductionLineService.enqueue(order, shortage)   // 생산 큐 등록

Service: ProductionLineService.enqueue(order, shortage)
  10. actualQty  = ceil(shortage / (order.sample.yield * 0.9))
  11. totalTime  = order.sample.avgProductionTime * actualQty
  12. new ProductionJob(jobId, order, shortage, actualQty, totalTime, startedAt=null)
  13. queue가 비어 있으면 job.startedAt = now()  // 즉시 시작
  14. jobQueue.add(job)

Controller
  15. "상태 변경  RESERVED → [PRODUCING]" + 주문번호 출력
```

**사후 상태**
- `order.status` = PRODUCING
- `ProductionLineService.jobQueue`에 ProductionJob 추가
- `sample.stock` 변동 없음 (생산 완료 후 증가)

---

## DF-07 — 주문 거절 (REJECTED)

**트리거**: SCR-3 재고 분석 화면에서 `[N]` 선택

```
Controller
  1. (SCR-3 재고 분석까지 DF-05/06 step 1~3 동일)
  2. [N] 입력

Service: OrderService.reject(orderId)
  3. OrderRepository.findById(orderId) → Order
  4. order.status != RESERVED 이면 throw InvalidOrderStateException
  5. order.transition(REJECTED)
  6. OrderRepository.save(order)

Controller
  7. "상태 변경  RESERVED → [REJECTED]" + "주문이 거절되었습니다." 출력
```

**사후 상태**
- `order.status` = REJECTED
- `sample.stock` 변동 없음

---

## DF-08 — 생산 자동 처리 (백그라운드 tick)

**트리거**: `ScheduledExecutorService` 1초 주기 자동 실행

```
ProductionLineService.tick()  [synchronized]
  1. currentJob = jobQueue.peek()
  2. currentJob == null → return (IDLE)
  3. currentJob.startedAt == null → currentJob.startedAt = now()  // 최초 시작 처리
  4. elapsed = now() - currentJob.startedAt  (milliseconds)
  5. totalMs  = currentJob.totalProductionTime * 60 * 1000  // min → ms
                (시연 모드: totalProductionTime * 1000 으로 가속 가능)

  6. elapsed < totalMs → return (진행 중)

  7. elapsed >= totalMs → 생산 완료:
       a. currentJob.order.sample.stock += currentJob.actualProductionQty
       b. currentJob.order.transition(CONFIRMED)
       c. OrderRepository.save(currentJob.order)
       d. jobQueue.poll()                    // 완료 작업 제거
       e. next = jobQueue.peek()
          if next != null: next.startedAt = now()  // 다음 작업 즉시 시작
```

**진행률 계산** (SCR-5 표시용)
```
getProgressPercent():
  elapsed = now() - currentJob.startedAt
  return min(100, (int)(elapsed * 100 / totalMs))

getEstimatedFinishTime():
  remainMs = totalMs - elapsed
  return LocalTime.now().plusSeconds(remainMs / 1000)
```

**동기화 범위**
- `tick()` 전체를 `synchronized(this)` 로 보호
- `getWaitingQueue()` / `getCurrentJob()` 도 동일 락 사용
- `sample.stock` 변경은 Sample 객체 자체에 `synchronized` 또는 Service 레벨 락

**사후 상태 (완료 시)**
- `sample.stock` 증가 (actualProductionQty 만큼)
- `order.status` = CONFIRMED
- `jobQueue`에서 해당 JobPoll 제거

---

## DF-09 — 출고 처리 (RELEASE)

**트리거**: SCR-6 번호 선택

```
Controller
  1. ReleaseService.getConfirmedOrders() → 목록 렌더링
  2. 번호 입력 → 해당 Order 조회
  3. ReleaseService.release(orderId) 호출

Service: ReleaseService.release(orderId)
  4. OrderRepository.findById(orderId) → Order
  5. order.status != CONFIRMED 이면 throw InvalidOrderStateException
  6. sample = order.sample
  7. sample.stock < order.quantity 이면 throw InsufficientStockException  // 안전 검증
  8. sample.stock -= order.quantity
  9. order.transition(RELEASE)
  10. order.releasedAt = LocalDateTime.now()
  11. OrderRepository.save(order)

Controller
  12. 주문번호, 출고수량, 처리일시, "CONFIRMED → [RELEASE]" 출력
```

**오류 경로**

| 예외 | 처리 |
|------|------|
| InvalidOrderStateException | "처리할 수 없는 주문 상태입니다." → 목록 재조회 |
| InsufficientStockException | "재고가 부족합니다. 관리자에게 문의하세요." |

**사후 상태**
- `sample.stock` 감소 (order.quantity 만큼)
- `order.status` = RELEASE
- `order.releasedAt` 기록

---

## DF-10 — 모니터링 조회

**트리거**: SCR-4에서 `[1]` 선택

```
Controller
  1. MonitoringService.getOrderSummaryByStatus() 호출
  2. MonitoringService.getStockStatus() 호출
  3. 두 결과를 하나의 화면에 렌더링

Service: MonitoringService.getOrderSummaryByStatus()
  4. OrderRepository.findAll()
  5. REJECTED 제외 후 status 기준 groupingBy + counting
  6. Map<OrderStatus, Long> 반환

Service: MonitoringService.getStockStatus()
  7. SampleRepository.findAll() 전체 순회
  8. 각 Sample 에 대해:
       pendingDemand = OrderRepository.findByStatus(RESERVED) + findByStatus(PRODUCING)
                       중 해당 sample 주문의 quantity 합계
       stockLabel    = 고갈/부족/여유 판정
       remainingRate = stock == 0 && pendingDemand == 0 ? 0
                     : stock + pendingDemand == 0 ? 0
                     : (int)(stock * 100.0 / (stock + pendingDemand))
  9. List<StockStatusDto> 반환

Controller
  10. 상태 배지 컬러 적용 후 출력
  11. 진행 바: floor(remainingRate / 10) 개의 █ + 나머지 ░
```

**사후 상태**: 변경 없음 (조회 전용)

---

## 상태 전이 제약 요약

`Order.transition(newStatus)` 내부에서 허용/불허 전이 검증.

| 현재 \ 다음 | RESERVED | REJECTED | PRODUCING | CONFIRMED | RELEASE |
|-------------|:--------:|:--------:|:---------:|:---------:|:-------:|
| RESERVED    | —        | ✅        | ✅         | ✅         | ❌       |
| REJECTED    | ❌        | —        | ❌         | ❌         | ❌       |
| PRODUCING   | ❌        | ❌        | —         | ✅         | ❌       |
| CONFIRMED   | ❌        | ❌        | ❌         | —         | ✅       |
| RELEASE     | ❌        | ❌        | ❌         | ❌         | —       |

불허 전이 시 `InvalidOrderStateException` throw.
