# Data Persistence — JSON 기반 영속성 전략

## 개요

모든 도메인 데이터를 `data/` 디렉터리의 JSON 파일에 저장한다.  
앱 시작 시 파일을 읽어 인메모리 저장소를 초기화하고, 변경 발생 시 즉시 파일에 반영한다.

---

## 라이브러리

**Gson 2.11.0** (Google)

```groovy
// build.gradle
implementation 'com.google.code.gson:gson:2.11.0'
```

Jackson 대비 설정 코드가 적고, 이 프로젝트의 단순한 도메인 구조에 충분하다.

---

## 저장 파일 구조

```
data/
├── samples.json           ← SampleRepository
├── orders.json            ← OrderRepository
└── production_jobs.json   ← ProductionLineService
```

앱 첫 실행 시 `data/` 디렉터리와 파일이 없으면 자동 생성, 빈 배열(`[]`)로 시작한다.

---

## JSON 포맷

### samples.json

```json
[
  {
    "id": "S-001",
    "name": "실리콘 웨이퍼-8인치",
    "avgProductionTime": 0.5,
    "yield": 0.92,
    "stock": 70,
    "reservedStock": 30
  }
]
```

- `stock`: 신규 주문에 가용한 재고 (approve 시 감소)
- `reservedStock`: 승인된 주문에 예약된 재고 (approve 시 증가, release 시 감소)
- 기존 JSON에 `reservedStock` 필드가 없으면 Gson이 0으로 역직렬화 (하위 호환)

### orders.json

`Order`는 `Sample` 객체 전체 대신 **`sampleId`만 저장**한다.  
로딩 후 `SampleRepository`에서 참조를 복원한다.

```json
[
  {
    "orderId": "ORD-20260416-0001",
    "sampleId": "S-001",
    "customerName": "삼성전자 파운드리",
    "quantity": 30,
    "status": "RESERVED",
    "createdAt": "2026-04-16T09:30:00",
    "releasedAt": null
  }
]
```

### production_jobs.json

`ProductionJob`도 `Order` 객체 전체 대신 **`orderId`만 저장**한다.

```json
[
  {
    "jobId": "PJ-20260416-0001",
    "orderId": "ORD-20260416-0001",
    "shortage": 30,
    "actualProductionQty": 37,
    "totalProductionTime": 19,
    "startedAt": "2026-04-16T09:31:00"
  }
]
```

---

## Repository 구현 패턴

모든 Repository는 동일한 패턴을 따른다.

```
생성자
  └─ loadFromFile()
       └─ 파일 없음 → 빈 컬렉션 반환
       └─ 파일 있음 → Gson으로 역직렬화 → 인메모리 저장소 초기화

변경 메서드(add / save / remove)
  └─ 인메모리 변경
  └─ saveToFile() → Gson으로 직렬화 → 파일 덮어쓰기
```

### GsonConfig (공통 Gson 설정)

`LocalDateTime`은 ISO-8601 문자열(`2026-04-16T09:30:00`)로 직렬화/역직렬화한다.

```java
// org.example.util.GsonConfig
public class GsonConfig {
    public static Gson create() {
        return new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .setPrettyPrinting()
            .create();
    }
}

class LocalDateTimeAdapter
        implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
    public JsonElement serialize(LocalDateTime src, ...) {
        return new JsonPrimitive(src.toString());
    }
    public LocalDateTime deserialize(JsonElement json, ...) {
        return LocalDateTime.parse(json.getAsString());
    }
}
```

---

## 참조 복원 (Reference Resolution)

JSON에는 ID만 저장하므로, 로딩 후 도메인 객체 참조를 복원해야 한다.

### AppContext 초기화 순서

```
1. SampleRepository  초기화  (samples.json 로드)
2. OrderRepository   초기화  (orders.json 로드 → sampleId로 Sample 참조 복원)
3. ProductionLineService 초기화
       (production_jobs.json 로드 → orderId로 Order 참조 복원)
```

### OrderRepository 참조 복원 예시

```java
List<OrderDto> dtos = gson.fromJson(reader, orderDtoListType);
for (OrderDto dto : dtos) {
    Sample sample = sampleRepository.findById(dto.sampleId)
        .orElseThrow(() -> new IllegalStateException("참조 시료 없음: " + dto.sampleId));
    Order order = dto.toOrder(sample);
    store.put(order.getOrderId(), order);
}
```

---

## 저장 시점

| Repository | 저장 트리거 |
|------------|------------|
| `SampleRepository` | `add()` 호출 시, `stock` 변경 시 |
| `OrderRepository` | `save()` 호출 시 (상태 전이 포함) |
| `ProductionLineService` | `enqueue()`, `tick()` 완료 시 |

`Sample.stock`과 `Sample.reservedStock`은 주문 승인·생산 완료·출고 시 변경되므로,  
`OrderService`, `ProductionLineService`, `ReleaseService`에서 변경 후 `SampleRepository.save(sample)`를 호출한다.

---

## DTO 클래스

JSON 직렬화 전용 내부 정적 클래스. 도메인 객체와 분리하여 JSON 구조 변경이 도메인에 영향을 주지 않도록 한다.

| DTO | 위치 | 용도 |
|-----|------|------|
| `OrderDto` | `OrderRepository` 내부 `private static class` | JSON ↔ Order 변환 |
| `ProductionJobDto` | `ProductionLineService` 내부 `private static class` | JSON ↔ ProductionJob 변환 |

`Sample`은 도메인 필드와 JSON 필드가 동일하므로 DTO 불필요.

---

## 테스트 전략

Repository 테스트에서 실제 파일 대신 임시 디렉터리를 사용한다.

```java
@TempDir
Path tempDir;

SampleRepository repo;

@BeforeEach
void setUp() {
    repo = new SampleRepository(tempDir.resolve("samples.json").toString());
}
```

각 Repository 생성자는 파일 경로를 파라미터로 받는다.  
`AppContext`에서는 기본 경로 `"data/samples.json"`을 사용한다.
