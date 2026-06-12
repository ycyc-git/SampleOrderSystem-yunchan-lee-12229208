# Phase 09 — 출고 처리 (RELEASE)

## 목표
CONFIRMED 상태 주문에 대해 출고를 실행하고 RELEASE로 전환한다.

## 전제 조건
Phase 05 완료 (CONFIRMED 주문이 존재할 수 있어야 함)  
Phase 08 완료 권장 (생산 완료 후 CONFIRMED된 주문도 출고 가능해야 함)

---
## 구현 대상

| 클래스 | 역할 |
|--------|------|
| `Order` | `releasedAt: LocalDateTime` 필드 추가, `CONFIRMED → PRODUCING` 전이 허용 |
| `ReleaseService` | `getConfirmedOrders()`, `release(orderId)`, `requeueToProducing(orderId)` |
| `ReleaseController` | `[6]` 출고 처리 연결, 재고 부족 시 재생산 큐 등록 흐름 |

**ReleaseService.release()**
```
※ 재고는 approve() 시점에 reservedStock으로 이미 확보됨
1. order.status != CONFIRMED → IllegalStateException
2. sample.reservedStock < order.quantity → IllegalStateException (예약 재고 부족)
3. sample.reservedStock -= order.quantity   // 예약 재고 차감 (가용 재고 불변)
4. order.transition(RELEASE)
5. order.releasedAt = LocalDateTime.now()
6. SampleRepository.save(sample), OrderRepository.save(order)
```

**ReleaseService.requeueToProducing()**
```
1. order.status != CONFIRMED → IllegalStateException
2. // 예약 반환: reservedStock → stock으로 복원
   toReturn = min(sample.reservedStock, order.quantity)
   sample.reservedStock -= toReturn
   sample.stock         += toReturn
3. shortage = order.quantity - sample.stock
4. shortage <= 0 → IllegalStateException (재고 충분, 재생산 불필요)
5. // 가용 재고를 다시 예약으로 이동
   newReserve = min(sample.stock, order.quantity)
   sample.reservedStock += newReserve
   sample.stock         -= newReserve
6. order.transition(PRODUCING)
7. OrderRepository.save(order)
8. ProductionLineService.enqueue(order, shortage)
```

**Order 상태 전이 추가**
```
CONFIRMED → PRODUCING  (재고 부족으로 출고 실패 후 재생산 큐 등록 시)
```

**화면 흐름** (→ screen-specs.md SCR-6 참조)
```
CONFIRMED 목록(번호 선택)
  → 출고 성공: 완료 화면(주문번호/출고수량/처리일시/[RELEASE] 배지)
  → 재고 부족: 오류 메시지 + Y/N 프롬프트
       Y → CONFIRMED → [PRODUCING] 전환 + 생산 큐 등록
       N → 취소 (상태 변경 없음)
```

---

## 수동 테스트 시나리오

```
시나리오 A — 재고 충분 승인 후 즉시 출고
1. S-001 (가용 재고 100) 주문 30ea → 승인 → CONFIRMED
   ※ 승인 시: 가용 재고 100 → 70, 예약 재고 0 → 30
2. 메인 → [6] 출고 처리
3. 목록에서 해당 주문 번호 선택
4. 완료 화면: 주문번호, 출고수량 30ea, 처리일시, CONFIRMED → [RELEASE] 확인
5. [4] 모니터링 → RELEASE 1건 증가, S-001 예약 재고 30 → 0 감소 확인
   (가용 재고는 70 그대로)

시나리오 B — 생산 완료 후 출고 (Phase 08 이후)
1. S-003 (재고 0) 주문 50ea → 승인 → PRODUCING
2. 생산 완료 대기 → CONFIRMED 자동 전환
3. [6] 출고 처리 → 목록에 나타남 확인
4. 출고 실행 → RELEASE 전환 확인

시나리오 C — CONFIRMED 없을 때
1. [6] 출고 처리 → "출고 가능한 주문이 없습니다." 출력 확인

시나리오 D — 재고 부족으로 출고 실패 후 재생산 큐 등록
1. S-001 (재고 100) 주문 200ea → 승인 → CONFIRMED (재고 부족으로 생산 필요 없음, 재고는 충분)
   ※ 테스트용: 먼저 다른 주문으로 재고를 대부분 소진한 뒤 남은 CONFIRMED 주문 시도
2. [6] 출고 처리 → 해당 주문 선택
3. "오류: 재고가 부족합니다." + "생산 큐에 다시 등록하시겠습니까?" 출력 확인
4. Y 입력 → CONFIRMED → [PRODUCING] 전환, 생산 큐 등록 확인
5. [5] 생산라인 조회 → 큐에 해당 작업 표시 확인

시나리오 E — 재고 부족 출고 실패 후 재생산 취소
1. 시나리오 D 1~3 동일
2. N 입력 → 상태 변경 없음 (CONFIRMED 유지) 확인

시나리오 F — 전체 흐름 연속 테스트
1. 시료 등록 (재고 포함)
2. 주문 접수
3. 승인 (재고 충분 or 부족)
4. (부족이면 생산 완료 대기)
5. 출고 처리
6. 모니터링에서 RELEASE 건수 및 재고 확인
```

---

## 완료 기준

- [x] `Order.releasedAt` 필드 추가
- [x] `Order.transition()` — `CONFIRMED → PRODUCING` 허용
- [x] `ReleaseService.release()` — 재고 차감, 상태 전환, 처리일시 기록
- [x] `ReleaseService.requeueToProducing()` — 재고 부족 시 PRODUCING 재전환 + 생산 큐 등록
- [x] `ReleaseController` — 번호 선택, 완료 화면(처리일시 포함)
- [x] `ReleaseController` — 재고 부족 시 Y/N 프롬프트 → Y: 재생산 큐 등록, N: 취소
- [x] 빈 목록 처리 (`"출고 가능한 주문이 없습니다."`)
- [x] 전체 주문 흐름 (등록 → 접수 → 승인 → 출고) 수동 확인
