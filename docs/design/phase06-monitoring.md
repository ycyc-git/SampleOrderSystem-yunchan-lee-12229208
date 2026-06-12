# Phase 06 — 모니터링

## 목표
상태별 주문 현황과 시료별 재고 현황을 모니터링 화면으로 확인할 수 있다.

## 전제 조건
Phase 05 완료 (RESERVED / CONFIRMED / PRODUCING / REJECTED 상태가 존재)

---

## 구현 대상

| 클래스 | 역할 |
|--------|------|
| `StockStatusDto` | sample, stock, pendingDemand, stockLabel, remainingRate |
| `MonitoringService` | `getOrderSummaryByStatus()`, `getStockStatus()` |
| `MonitoringController` | `[4]` 모니터링 연결, 서브메뉴 [1][2][0] |

**MonitoringService.getOrderSummaryByStatus()**
```
OrderRepository.findAll() 에서 REJECTED 제외
→ status 기준 groupingBy + counting
→ Map<OrderStatus, Long>
```

**MonitoringService.getStockStatus()**
```
각 Sample에 대해:
  pendingDemand = RESERVED + PRODUCING 주문 중 해당 sample의 quantity 합계
  stockLabel    = stock==0 ? "고갈" : stock < pendingDemand ? "부족" : "여유"
  remainingRate = (stock + pendingDemand == 0) ? 0
                : (int)(stock * 100.0 / (stock + pendingDemand))
```

**화면 출력** (→ screen-specs.md SCR-4 참조)
- `[1]` 선택: 주문 현황(배지 + 건수) + 재고 현황(시료명/재고/레이블배지/진행바) 통합 표시
- `[2]` 선택: 재고 현황만 표시
- 진행 바: `floor(remainingRate / 10)` 개의 `█` + 나머지 `░` (10칸)

---

## 수동 테스트 시나리오

```
1. Phase 05 데이터 유지 상태에서 진행
   (CONFIRMED 1건, PRODUCING 1건, REJECTED 1건 이상 존재)
2. 메인 → [4] 모니터링 → [1] 주문량 확인
3. 상태별 주문 현황 확인:
   - RESERVED, CONFIRMED, PRODUCING, RELEASE 건수 표시
   - REJECTED는 표시 안됨 확인
   - PRODUCING 옆 "← 생산라인 대기" 주석 확인
4. 재고 현황 확인:
   - S-001: 재고 70, 여유, 진행 바 표시
   - S-003: 재고 0, 고갈, 진행 바 0%
5. [2] 재고량 확인 → 재고 현황만 단독 표시 확인
6. [0] 뒤로 → 메인 복귀
```

---

## 완료 기준

- [ ] `StockStatusDto` 및 `MonitoringService` 구현
- [ ] 상태별 주문 수 집계 (REJECTED 제외)
- [ ] 재고 레이블 판정 (여유/부족/고갈)
- [ ] 잔여율 계산 및 진행 바 출력 (10칸)
- [ ] `[1]` 통합 화면, `[2]` 재고 단독 화면
- [ ] 배지 컬러 (ANSI escape code): RESERVED(파랑), CONFIRMED(초록), PRODUCING(주황), RELEASE(보라), 여유(초록), 부족(주황), 고갈(빨강)
