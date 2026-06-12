# Phase 07 — 생산 큐 등록 + 생산라인 조회 (정적)

## 목표
재고 부족 승인 시 생산 큐에 작업을 등록하고, 생산라인 조회 화면에서 큐 내용을 확인할 수 있다.  
이 Phase에서 생산은 자동으로 진행되지 않는다 (백그라운드 tick은 Phase 08).

## 전제 조건
Phase 05 완료

---

## 구현 대상

| 클래스 | 역할 |
|--------|------|
| `ProductionJob` | 도메인. 필드: jobId, order, shortage, actualProductionQty, totalProductionTime(min), startedAt(nullable) |
| `ProductionLineService` | `enqueue(order, shortage)`, `getCurrentJob()`, `getWaitingQueue()`, `getProgressPercent()`, `getEstimatedFinishTime()` |
| `ProductionLineController` | `[5]` 생산라인 조회 연결 |
| `OrderService.approve()` | 재고 부족 분기에 `ProductionLineService.enqueue()` 호출 추가 |

**ProductionJob 생성 (enqueue 시)**
```
actualQty  = ceil(shortage / (sample.yield * 0.9))
totalTime  = sample.avgProductionTime * actualQty   (단위: min)
startedAt  = queue가 비어 있으면 now(), 아니면 null (대기 상태)
```

**화면 출력** (→ screen-specs.md SCR-5 참조)
- 현재 생산 중 (startedAt != null):
  - 주문번호, 시료명, 주문량→재고→부족→실생산량 흐름, 진행률, 완료 예정 시각
  - 진행률 = `getProgressPercent()` (tick 없으므로 이 Phase에서는 0% 고정)
- 대기 큐 (FIFO 순): 순서 / 주문번호 / 시료 / 주문량 / 부족분 / 실생산량 / 예상 완료
- IDLE 상태: `"현재 생산 중인 작업이 없습니다. [IDLE]"`
- 하단 공식 주석 출력

**메인 현황 연동**
- `생산라인 N건 대기`: 현재 처리 중 + 대기 큐 크기 합산

---

## 수동 테스트 시나리오

```
1. Phase 05 데이터 유지 (PRODUCING 주문 1건 이상)
   없으면: S-003 (재고 0) 주문 50ea 접수 → 승인 → PRODUCING
2. 메인 → [5] 생산라인 조회
3. "현재 처리 중" 섹션:
   - 첫 번째 PRODUCING 주문 정보 표시 확인
   - 진행률 0% (tick 없음)
4. 추가 PRODUCING 주문 생성 (S-002 재고 0으로 소진 후 주문 → 승인)
5. 생산라인 조회 재진입 → 대기 큐에 2번째 작업 표시 확인
6. 메인 현황 "생산라인 N건 대기" 반영 확인
```

---

## 완료 기준

- [ ] `ProductionJob` 도메인 구현
- [ ] `ProductionLineService.enqueue()` — actualQty, totalTime 계산, startedAt 설정
- [ ] `OrderService.approve()` 재고 부족 분기에서 enqueue 호출
- [ ] `ProductionLineController` — RUNNING/IDLE 배지, 현재 처리 중 섹션, 대기 큐 7개 컬럼
- [ ] 메인 현황 "생산라인 N건 대기" 연동
