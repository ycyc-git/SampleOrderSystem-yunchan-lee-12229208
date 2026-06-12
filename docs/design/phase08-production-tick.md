# Phase 08 — 생산 자동 처리 (백그라운드 tick)

## 목표
백그라운드 스케줄러가 생산 시간을 경과시키고, 완료된 작업의 주문 상태를 자동으로 CONFIRMED로 전환한다.  
생산라인 조회에서 진행률이 실시간으로 갱신된다.

## 전제 조건
Phase 07 완료

---

## 구현 대상

| 클래스 | 변경/추가 내용 |
|--------|---------------|
| `ProductionLineService` | `tick()` 완성, `ScheduledExecutorService` 연동 |
| `AppContext` | 앱 시작 시 스케줄러 시작, 종료 시 shutdown |

**tick() 동작** (→ data-flows.md DF-08 참조)
```
[synchronized]
1. currentJob = jobQueue.peek()
2. null → return (IDLE)
3. elapsed = now() - startedAt  (ms)
4. totalMs  = totalProductionTime * 60 * 1000
              ※ 시연 모드: * 1000 (분→초 가속, 구현 시 상수로 선택)
5. elapsed < totalMs → return
6. 완료:
   a. shortage  = currentJob.shortage           // 승인 시 계산된 부족분
      actualQty = currentJob.actualProductionQty
      sample.stock         += (actualQty - shortage)  // 생산 잉여분만 가용 재고
      sample.reservedStock += shortage                // 부족분은 예약 재고로 이동
      ※ 승인 시 기존 가용 재고는 이미 reservedStock으로 이동됨
        생산 완료 후 reservedStock = 기존예약 + shortage = order.quantity 전량 확보
   b. order.transition(CONFIRMED)
   c. OrderRepository.save(order)
   d. jobQueue.poll()
   e. 다음 작업 있으면 peek().startedAt = now()
```

**스케줄러 설정**
```java
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(productionLineService::tick, 0, 1, TimeUnit.SECONDS);
// AppContext.stop() 에서 scheduler.shutdownNow()
```

**동기화 범위**
- `tick()`, `enqueue()`, `getCurrentJob()`, `getWaitingQueue()` 모두 `synchronized(this)` 또는 단일 락

**진행률 계산 (getProgressPercent)**
```
elapsed = now() - startedAt
return (int) Math.min(100, elapsed * 100 / totalMs)
```

**완료 예정 시각 (getEstimatedFinishTime)**
```
remainMs = max(0, totalMs - elapsed)
return LocalTime.now().plusSeconds(remainMs / 1000)
```

---

## 수동 테스트 시나리오

> 시연 편의를 위해 tick의 시간 단위를 **초(second) 모드**로 설정 권장  
> (avgProductionTime 0.5 min/ea × 실생산량 61ea = 30.5 → 약 30초 대기)

```
1. S-003 (재고 0, avgTime 0.8, yield 0.92) 주문 50ea 접수 → 승인 → PRODUCING
   실생산량 = ceil(50 / (0.92 * 0.9)) = ceil(60.4) = 61 ea
   총 시간  = 0.8 * 61 = 48.8 min → 초 모드에서 49초 대기

2. [5] 생산라인 조회 → 진행률이 증가하는 것 확인 (매 진입마다 갱신)

3. 49초 후 [5] 생산라인 조회 재진입
   → 해당 작업이 큐에서 사라짐 확인 (또는 다음 작업으로 전환)

4. [4] 모니터링 → 주문 상태가 PRODUCING → CONFIRMED로 변경 확인

5. S-003 확인: 가용 재고 = actualQty - shortage = 잉여분, 예약 재고 = shortage (61 ea)

6. 메인 "생산라인 N건 대기" 감소 확인

7. [0] 종료 → 정상 종료 (스케줄러 shutdown) 확인
```

---

## 완료 기준

- [ ] `tick()` 완료 조건 및 상태 전환 구현
- [ ] `ScheduledExecutorService` 1초 주기 연동
- [ ] 진행률 `getProgressPercent()` 및 `getEstimatedFinishTime()` 동작
- [ ] 생산 완료 시 `sample.stock` += (actualQty - shortage), `sample.reservedStock` += shortage, `order.status` = CONFIRMED
- [ ] 다음 대기 작업 자동 시작
- [ ] 동기화 — 콘솔 루프와 tick 간 race condition 없음
- [ ] 앱 종료 시 스케줄러 정상 shutdown
