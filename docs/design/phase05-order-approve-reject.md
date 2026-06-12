# Phase 05 — 주문 승인/거절

## 목표
RESERVED 주문에 대해 재고 분석 후 승인(CONFIRMED 또는 PRODUCING) 또는 거절(REJECTED)을 처리한다.  
이 Phase에서 생산 큐 등록은 하지 않으며, 재고 부족 시 주문 상태만 PRODUCING으로 전환한다.

## 전제 조건
Phase 04 완료

---

## 구현 대상

| 클래스 | 변경/추가 내용 |
|--------|---------------|
| `Order` | `transition(OrderStatus)` — 허용 전이만 통과, 불허 시 `IllegalStateException` |
| `OrderService` | `approve(orderId)`, `reject(orderId)`, `getReservedOrders()`, `analyzeStock(orderId)` |
| `OrderController` | `[3]` 주문 승인/거절 연결 |

**OrderService.analyzeStock() 반환 구조**
```
shortage       = max(0, order.quantity - sample.stock)
actualQty      = ceil(shortage / (sample.yield * 0.9))   // shortage > 0 일 때
totalTime(min) = sample.avgProductionTime * actualQty     // shortage > 0 일 때
```

**OrderService.approve() 처리 분기**
```
shortage == 0 → sample.stock -= quantity → order.transition(CONFIRMED)
shortage  > 0 → order.transition(PRODUCING)
               ※ 생산 큐 등록은 Phase 07에서 추가
```

**OrderService.reject()**
```
order.transition(REJECTED)
```

**화면 흐름** (→ screen-specs.md SCR-3 참조)
```
RESERVED 목록(번호 선택) → 재고 분석 자동 표시 → [Y] 승인 / [N] 거절 → 결과
```

**상태 전이 허용 범위 (이 Phase)**

| 현재 | 다음 | 조건 |
|------|------|------|
| RESERVED | CONFIRMED | 재고 충분 |
| RESERVED | PRODUCING | 재고 부족 |
| RESERVED | REJECTED | [N] 선택 |

---

## 수동 테스트 시나리오

```
시나리오 A — 재고 충분 → CONFIRMED
1. Phase 02~04 데이터 유지 (S-001 재고 100, 주문 30ea)
2. 메인 → [3] 주문 승인/거절
3. RESERVED 목록 확인 (S-001 30ea 주문 포함)
4. 해당 번호 선택
5. 재고 분석: 현재 재고 100, 부족분 0 → "재고 충분" 표시 확인
6. [Y] → RESERVED → [CONFIRMED] 상태 변경 확인
7. 메인 복귀 → S-001 재고가 70으로 감소했는지 확인 (총 재고 감소)

시나리오 B — 재고 부족 → PRODUCING
1. S-003 (재고 0) 주문 50ea 접수
2. [3] 주문 승인/거절 → 해당 주문 선택
3. 재고 분석: 현재 재고 0, 부족분 50, 실생산량 N, 총 N min 표시 확인
4. [Y] → RESERVED → [PRODUCING] 상태 변경 확인

시나리오 C — 거절
1. 새 주문 접수
2. 승인 화면에서 [N] → RESERVED → [REJECTED] 확인
3. REJECTED 주문이 RESERVED 목록에 더 이상 표시되지 않는지 확인
```

---

## 완료 기준

- [ ] `Order.transition()` 상태 전이 검증
- [ ] `analyzeStock()` 계산 (재고, 부족분, 실생산량, 총 시간)
- [ ] 재고 분석 화면 자동 표시 (재고 충분/부족 분기 메시지)
- [ ] 승인 → CONFIRMED (재고 차감) / PRODUCING (차감 없음)
- [ ] 거절 → REJECTED
- [ ] 잘못된 번호 입력 재시도
