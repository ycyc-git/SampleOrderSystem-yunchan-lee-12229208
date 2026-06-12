# Phase 04 — 주문 접수 (RESERVED)

## 목표
고객 주문을 접수하여 RESERVED 상태의 주문을 생성한다.  
메인 화면의 "전체 주문 N건" 현황이 실시간으로 반영된다.

## 전제 조건
Phase 02 완료 (시료가 등록되어 있어야 주문 가능)

---

## 구현 대상

| 클래스 | 역할 |
|--------|------|
| `OrderStatus` | 열거형: RESERVED, REJECTED, PRODUCING, CONFIRMED, RELEASE |
| `Order` | 도메인. 필드: orderId, sample, customerName, quantity, status, createdAt, releasedAt |
| `OrderRepository` | 인메모리 + JSON 파일 영속. `save`, `findById`, `findByStatus`, `findAll` |
| `OrderService` | `reserve(sampleId, customerName, quantity)` |
| `OrderController` | `[2]` 시료 주문 연결 (입력 → 확인 → 완료) |

**JSON 영속성**
- 파일: `data/orders.json`
- JSON에 `Sample` 객체 전체가 아닌 `sampleId`(String)만 저장
- `OrderRepository(String filePath, SampleRepository sampleRepo)` 생성자
  - 로딩 시 `sampleId`로 `SampleRepository`에서 `Sample` 참조 복원
- `save(order)` 호출 시 즉시 파일 저장 (상태 전이 포함)
- JSON 포맷은 `docs/design/data-persistence.md` 참조

**주문 ID 생성 규칙**
```
"ORD-" + LocalDate.now().format("yyyyMMdd") + "-" + 오늘 일련번호(4자리 zero-pad)
예) ORD-20260416-0001
```

**OrderService.reserve() 유효성 규칙**

| 검증 | 오류 메시지 |
|------|-------------|
| sampleId 미등록 | `"등록되지 않은 시료 ID입니다."` |
| quantity ≤ 0 | `"주문 수량은 1 이상이어야 합니다."` |

**화면 흐름** (→ screen-specs.md SCR-2 참조)
```
입력(시료ID / 고객명 / 수량) → 확인 화면(Y/N) → 완료(주문번호 + [RESERVED] 배지)
```

**메인 현황 연동**
- `전체 주문 N건` : REJECTED 제외 전체 주문 수

---

## 수동 테스트 시나리오

```
1. Phase 02 데이터 유지 (S-001 재고 100)
2. 메인 → [2] 시료 주문
3. S-001 / 삼성전자 파운드리 / 30 입력
4. 확인 화면에서 시료명(S-001명), 고객명, 수량 표시 확인
5. [Y] → 주문번호 ORD-YYYYMMDD-0001, [RESERVED] 배지 출력 확인
6. 다시 [2] 시료 주문 → S-002 / SK하이닉스 / 20 접수
7. 세 번째 주문: [N] 취소 → 주문 미생성 확인
8. 메인 복귀 → 전체 주문 2건 확인
9. 없는 시료 ID(S-999) 입력 → 오류 메시지 확인
```

---

## 완료 기준

- [ ] `Order` 도메인 및 `OrderRepository` 구현
- [ ] `OrderService.reserve()` 유효성 검증 포함
- [ ] 입력 → 확인(Y/N) → 완료 화면 흐름
- [ ] 주문 ID 자동 생성 (`ORD-YYYYMMDD-XXXX`)
- [ ] 메인 현황 "전체 주문 N건" 실시간 반영
- [ ] `[N]` 취소 시 주문 미생성 확인
- [ ] `data/orders.json` 영속 (`sampleId`만 저장, 재시작 후 참조 복원)
