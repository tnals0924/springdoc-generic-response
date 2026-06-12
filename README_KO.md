# springdoc-generic-response

springdoc-openapi의 제네릭 타입 flatten 문제를 해결하는 Spring Boot 라이브러리.

springdoc은 `CommonResponse<T>`를 반환하는 엔드포인트의 OpenAPI 스펙을 생성할 때,
제네릭 타입을 `CommonResponseTestResponse`처럼 이름을 연결해 flatten합니다.
이 때문에 swagger-typescript-api 같은 프론트엔드 타입 생성 도구가 제네릭 없는,
재사용 불가능한 타입을 만들어냅니다.

이 라이브러리는 flatten된 스키마를 `allOf` + `x-extension` 구조로 재작성해,
프론트엔드에서 `CommonResponse<TestResponse>` 같은 올바른 제네릭 타입을 복원할 수 있게 합니다.

## 문제

```yaml
# springdoc 기본 동작 — 타입 폭발, 제네릭 없음
CommonResponseTestResponse:
  properties: { status, code, message, data: $ref TestResponse }

CommonResponseCursorSliceResponseProjectSummaryResponse:
  properties: { status, code, message, data: { content: [...], hasNext, ... } }
```

```typescript
// swagger-typescript-api 출력 — 제네릭 없음, 재사용 불가
export interface CommonResponseTestResponse {
  status?: number;
  code?: string;
  data?: TestResponse;
}
```

## 해결

```yaml
# 라이브러리 적용 후
CommonResponse:
  properties: { status, code, message }

CommonResponseTestResponse:
  allOf:
    - $ref: CommonResponse
    - properties: { data: { $ref: TestResponse } }
  x-generic-base: CommonResponse
  x-generic-type-arg: TestResponse

CommonResponseCursorSliceResponseProjectSummaryResponse:
  allOf:
    - $ref: CommonResponse
    - properties: { data: { $ref: CursorSliceResponseProjectSummaryResponse } }
  x-generic-base: CommonResponse
  x-generic-type-arg: "CursorSliceResponse<ProjectSummaryResponse>"
```

```typescript
// swagger-typescript-api 출력 — 올바른 제네릭
export type CommonResponseTestResponse = CommonResponse<TestResponse>;
export type CommonResponseCursorSliceResponseProjectSummaryResponse =
  CommonResponse<CursorSliceResponse<ProjectSummaryResponse>>;
```

## 요구사항

- Spring Boot 3.x
- springdoc-openapi 2.x (`springdoc-openapi-starter-webmvc-ui`)
- Kotlin / Java

## 사용법

### 1. 의존성 추가

```kotlin
// build.gradle.kts
implementation("io.github.tnals0924:springdoc-generic-response:1.0.0")
```

### 2. 제네릭 래퍼 클래스에 어노테이션 추가

```kotlin
@GenericWrapper(dataField = "data")
data class CommonResponse<T>(
    val status: Int,
    val code: String,
    val message: String,
    val data: T? = null
)

@GenericWrapper(dataField = "content")
data class CursorSliceResponse<T>(
    val content: List<T>,
    val size: Int,
    val hasNext: Boolean,
    val nextCursorId: Long?,
    val nextCursorValue: String?
)
```

이게 전부입니다. Spring Boot AutoConfiguration이 `@GenericWrapper` 클래스를 자동으로 감지해
별도 설정 없이 동작합니다.

### 3. (선택) 프론트엔드 연동 — swagger-typescript-api

`swagger-typescript-api` 설정에 아래 5줄 훅을 추가하면 제네릭 타입이 복원됩니다.

```javascript
hooks: {
  onParseSchema: (originalSchema, parsedSchema) => {
    if (originalSchema?.["x-generic-base"] && originalSchema?.["x-generic-type-arg"]) {
      parsedSchema.content =
        `${originalSchema["x-generic-base"]}<${originalSchema["x-generic-type-arg"]}>`;
    }
    return parsedSchema;
  }
}
```

## 설정

모든 설정은 선택 사항이며, 기본값만으로 동작합니다.

```yaml
# application.yml
generic-response:
  enabled: true          # 라이브러리 전체 비활성화 (기본값: true)
  auto-detect: true      # @GenericWrapper 없는 클래스도 자동 감지 (기본값: true)
  base-packages:         # 스캔할 패키지 (기본값: @SpringBootApplication 패키지 자동 감지)
    - com.example.response
```

### `auto-detect`

`true`로 설정하면 `@GenericWrapper`가 **없는** 클래스도 자동으로 감지합니다.
단일 타입 파라미터를 가지며 해당 타입 파라미터를 직접 사용하는 필드(`T` 또는 `List<T>`)가
있는 클래스를 래퍼로 식별합니다.

> 동일한 클래스 단순명이 여러 개 존재하면 이름 충돌 위험이 있습니다.
> 가능하면 `@GenericWrapper` 어노테이션을 명시하는 것을 권장합니다.

## 동작 원리

1. **`@GenericWrapper`** — 클래스를 제네릭 래퍼로 표시하고, `T`가 들어가는 필드명을 선언합니다.

2. **`FlattenedTypeNameParser`** — `CommonResponseCursorSliceResponseProjectSummaryResponse` 같은
   flatten된 이름을 트리 구조로 파싱합니다.
   ```
   Generic("CommonResponse",
     Generic("CursorSliceResponse",
       Leaf("ProjectSummaryResponse")))
   ```
   알려진 제네릭 이름 목록에서 longest-match로 prefix를 탐색해 모호한 분리를 방지합니다.

3. **`GenericSchemaRewriter`** — 각 flat 스키마를 다음 구조로 재작성합니다.
   - `allOf [$ref: 베이스타입, {dataField: 원본데이터프로퍼티}]`
   - `x-generic-base`: 베이스 타입명
   - `x-generic-type-arg`: 타입 인자 문자열 (예: `CursorSliceResponse<ProjectSummaryResponse>`)
   - 베이스 스키마(`CommonResponse` 등)가 없으면 자동으로 생성합니다.

4. **`GenericResponseCustomizer`** — springdoc의 `OpenApiCustomizer`를 구현합니다.
   스펙 생성 완료 후 한 번 실행되어 매칭되는 모든 스키마를 재작성합니다.

5. **`GenericResponseAutoConfiguration`** — Spring Boot AutoConfiguration 진입점입니다.
   `@GenericWrapper` 어노테이션이 붙은 클래스를 스캔해 customizer를 빈으로 등록합니다.

## 지원 케이스

| 케이스 | flat 스키마명 예시 | x-generic-type-arg |
|--------|-------------------|--------------------|
| 1단계 | `CommonResponseTestResponse` | `TestResponse` |
| 2단계 중첩 | `CommonResponseCursorSliceResponseProjectSummaryResponse` | `CursorSliceResponse<ProjectSummaryResponse>` |
| 3단계 중첩 | `CommonResponseCursorSliceResponsePageWrapperProjectSummaryResponse` | `CursorSliceResponse<PageWrapper<ProjectSummaryResponse>>` |
| data 없음 | `CommonResponseObject` | `Object` |

## 프로젝트 구조

```
springdoc-generic-response/
├── lib/                          # 배포되는 라이브러리
│   └── src/main/kotlin/io/github/tnals0924/genericresponse/
│       ├── annotation/           # @GenericWrapper
│       ├── model/                # GenericWrapperInfo
│       ├── parser/               # FlattenedTypeNameParser
│       ├── rewriter/             # GenericSchemaRewriter
│       ├── customizer/           # GenericResponseCustomizer (OpenApiCustomizer)
│       ├── detector/             # GenericWrapperDetector (클래스패스 스캔 + 자동 감지)
│       ├── properties/           # GenericResponseProperties (@ConfigurationProperties)
│       └── autoconfigure/       # GenericResponseAutoConfiguration
└── sample/                       # 동작 확인용 Spring Boot 샘플 앱
```

## 샘플 앱 실행

```bash
./gradlew :sample:bootRun
# OpenAPI 스펙: http://localhost:8080/v3/api-docs
# Swagger UI:  http://localhost:8080/swagger-ui.html
```

## 테스트 실행

```bash
./gradlew :lib:test
```
