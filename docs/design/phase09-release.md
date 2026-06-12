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
| `Order` | `releasedAt: LocalDateTime` 필드 추가 |
| `ReleaseService` | `getConfirmedOrders()`, `release(orderId)` |
| `ReleaseController` | `[6]` 출고 처리 연결 |

**ReleaseService.release()**
```
1. order.status != CONFIRMED → IllegalStateException
2. sample.stock < order.quantity → InsufficientStockException (안전 검증)
3. sample.stock -= order.quantity
4. order.transition(RELEASE)
5. order.releasedAt = LocalDateTime.now()
6. OrderRepository.save(order)
```

**화면 흐름** (→ screen-specs.md SCR-6 참조)
```
CONFIRMED 목록(번호 선택) → 완료 화면(주문번호/출고수량/처리일시/[RELEASE] 배지)
```

---

## 수동 테스트 시나리오

```
시나리오 A — 재고 충분 승인 후 즉시 출고
1. S-001 (재고 100) 주문 30ea → 승인 → CONFIRMED
2. 메인 → [6] 출고 처리
3. 목록에서 해당 주문 번호 선택
4. 완료 화면: 주문번호, 출고수량 30ea, 처리일시, CONFIRMED → [RELEASE] 확인
5. [4] 모니터링 → RELEASE 1건 증가, S-001 재고 감소 확인

시나리오 B — 생산 완료 후 출고 (Phase 08 이후)
1. S-003 (재고 0) 주문 50ea → 승인 → PRODUCING
2. 생산 완료 대기 → CONFIRMED 자동 전환
3. [6] 출고 처리 → 목록에 나타남 확인
4. 출고 실행 → RELEASE 전환 확인

시나리오 C — CONFIRMED 없을 때
1. [6] 출고 처리 → "출고 가능한 주문이 없습니다." 출력 확인

시나리오 D — 전체 흐름 연속 테스트
1. 시료 등록 (재고 포함)
2. 주문 접수
3. 승인 (재고 충분 or 부족)
4. (부족이면 생산 완료 대기)
5. 출고 처리
6. 모니터링에서 RELEASE 건수 및 재고 확인
```

---

## 완료 기준

- [ ] `Order.releasedAt` 필드 추가
- [ ] `ReleaseService.release()` — 재고 차감, 상태 전환, 처리일시 기록
- [ ] `ReleaseController` — 번호 선택, 완료 화면(처리일시 포함)
- [ ] 빈 목록 처리 (`"출고 가능한 주문이 없습니다."`)
- [ ] 전체 주문 흐름 (등록 → 접수 → 승인 → 출고) 수동 확인
