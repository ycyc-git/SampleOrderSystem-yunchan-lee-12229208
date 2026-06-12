# Phase 02 — 시료 등록 + 목록

## 목표
시료를 등록하고 목록으로 조회할 수 있다.  
메인 화면의 "등록 시료 N종 / 총 재고 N ea" 현황이 실시간으로 반영된다.

## 전제 조건
Phase 01 완료

---

## 구현 대상

| 클래스 | 역할 |
|--------|------|
| `Sample` | 도메인. 필드: id, name, avgProductionTime(double, min/ea), yield(double), stock(int) |
| `SampleRepository` | `Map<String, Sample>` 인메모리 저장. `add`, `findById`, `findAll` |
| `SampleService` | `register(id, name, avgTime, yield, initialStock)`, `getAll()` |
| `SampleController` | `[1]` 시료 등록, `[2]` 시료 목록, `[3]` → "준비 중" |

> `initialStock` : 테스트 편의를 위해 등록 시 초기 재고 입력 허용 (0 이상 정수)

**SampleService.register() 유효성 규칙**

| 필드 | 규칙 |
|------|------|
| id | 비어 있지 않음, 중복 없음 |
| name | 비어 있지 않음 |
| avgProductionTime | > 0 |
| yield | 0.0 < yield ≤ 1.0 |
| initialStock | ≥ 0 |

**시료 목록 출력**
- 5개/페이지, `[N]` 다음페이지, `[P]` 이전페이지
- 컬럼: ID / 시료명 / 평균 생산시간 / 수율 / 현재 재고
- 빈 목록 시: `"등록된 시료가 없습니다."`

**메인 현황 연동**
- `등록 시료 N종` : `SampleRepository.findAll().size()`
- `총 재고 N ea` : 전체 `sample.stock` 합계

---

## 수동 테스트 시나리오

```
1. 메인 → [1] 시료 관리 → [1] 시료 등록
2. 아래 3개 시료 순서대로 등록
   S-001 / 실리콘 웨이퍼-8인치 / 0.5 / 0.92 / 초기재고 100
   S-002 / GaN 에피택셀-4인치  / 0.3 / 0.78 / 초기재고 50
   S-003 / SiC 파워기판-6인치  / 0.8 / 0.92 / 초기재고 0
3. [2] 시료 목록 → 3개 행 출력, 재고 값 확인
4. 메인으로 돌아와 시스템 현황 확인
   → 등록 시료 3종, 총 재고 150 ea
5. 중복 ID(S-001) 등록 시도 → 오류 메시지 확인
6. 수율 1.5 입력 → 오류 메시지 확인
```

---

## 완료 기준

- [ ] 시료 등록 (유효성 검증 포함)
- [ ] 시료 목록 (5개/페이지, 페이지네이션)
- [ ] 메인 현황 "등록 시료 / 총 재고" 실시간 반영
- [ ] 중복 ID, 잘못된 수율/생산시간 오류 처리
