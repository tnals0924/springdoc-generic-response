# springdoc-generic-response

A Spring Boot library that fixes generic type flattening in springdoc-openapi.

When springdoc generates OpenAPI specs for endpoints returning `CommonResponse<T>`, it flattens
generic types into concatenated names like `CommonResponseTestResponse`. This makes frontend type
generation tools (e.g. swagger-typescript-api) produce non-generic, non-reusable types.

This library rewrites those flattened schemas into `allOf` + `x-extension` structures so that
frontends can reconstruct proper generic types like `CommonResponse<TestResponse>`.

## Problem

```yaml
# springdoc default — type explosion, no generics
CommonResponseTestResponse:
  properties: { status, code, message, data: $ref TestResponse }

CommonResponseCursorSliceResponseProjectSummaryResponse:
  properties: { status, code, message, data: { content: [...], hasNext, ... } }
```

```typescript
// swagger-typescript-api output — no generics, not reusable
export interface CommonResponseTestResponse {
  status?: number;
  code?: string;
  data?: TestResponse;
}
```

## Solution

```yaml
# After this library
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
// swagger-typescript-api output — proper generics
export type CommonResponseTestResponse = CommonResponse<TestResponse>;
export type CommonResponseCursorSliceResponseProjectSummaryResponse =
  CommonResponse<CursorSliceResponse<ProjectSummaryResponse>>;
```

## Requirements

- Spring Boot 3.x
- springdoc-openapi 2.x (`springdoc-openapi-starter-webmvc-ui`)
- Kotlin / Java

## Usage

### 1. Add the dependency

```kotlin
// build.gradle.kts
implementation("io.github.tnals0924:springdoc-generic-response:1.0.0")
```

### 2. Annotate your generic wrapper classes

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

That's it. Spring Boot AutoConfiguration detects `@GenericWrapper` classes automatically — no
additional configuration required.

### 3. (Optional) Frontend integration — swagger-typescript-api

Add this 5-line hook to your `swagger-typescript-api` config to reconstruct generic types:

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

## Configuration

All settings are optional. Zero-config works out of the box.

```yaml
# application.yml
generic-response:
  enabled: true          # disable the library entirely (default: true)
  auto-detect: true      # detect wrapper classes without @GenericWrapper (default: true)
  base-packages:         # packages to scan (default: auto-detected from @SpringBootApplication)
    - com.example.response
```

### `auto-detect`

When `true`, the library also scans for generic wrapper classes that are **not** annotated
with `@GenericWrapper`. It identifies them by looking for classes with a single type parameter
whose field directly uses that type parameter (`T` or `List<T>`).

> This carries a name-collision risk if multiple classes share the same simple name. Prefer
> explicit `@GenericWrapper` annotation when possible.

## How it works

1. **`@GenericWrapper`** — marks a class as a generic wrapper and declares which field holds `T`.

2. **`FlattenedTypeNameParser`** — parses a flat name like
   `CommonResponseCursorSliceResponseProjectSummaryResponse` into a tree:
   ```
   Generic("CommonResponse",
     Generic("CursorSliceResponse",
       Leaf("ProjectSummaryResponse")))
   ```
   Uses longest-match on known generic names to avoid ambiguous splits.

3. **`GenericSchemaRewriter`** — rewrites each flat schema into:
   - `allOf [$ref: BaseType, {dataField: originalDataProperty}]`
   - `x-generic-base`: base type name
   - `x-generic-type-arg`: reconstructed type argument string (e.g. `CursorSliceResponse<ProjectSummaryResponse>`)
   - Automatically creates the base schema (e.g. `CommonResponse`) if it does not yet exist.

4. **`GenericResponseCustomizer`** — implements springdoc's `OpenApiCustomizer`. Runs after spec
   generation and rewrites all matching schemas in one pass.

5. **`GenericResponseAutoConfiguration`** — Spring Boot AutoConfiguration entry point. Scans for
   `@GenericWrapper` annotated classes and registers the customizer as a bean.

## Supported cases

| Case | Example flat name | x-generic-type-arg |
|------|-------------------|--------------------|
| 1-level | `CommonResponseTestResponse` | `TestResponse` |
| 2-level nested | `CommonResponseCursorSliceResponseProjectSummaryResponse` | `CursorSliceResponse<ProjectSummaryResponse>` |
| 3-level nested | `CommonResponseCursorSliceResponsePageWrapperProjectSummaryResponse` | `CursorSliceResponse<PageWrapper<ProjectSummaryResponse>>` |
| No data field | `CommonResponseObject` | `Object` |

## Project structure

```
springdoc-generic-response/
├── lib/                          # Published library
│   └── src/main/kotlin/io/github/tnals0924/genericresponse/
│       ├── annotation/           # @GenericWrapper
│       ├── model/                # GenericWrapperInfo
│       ├── parser/               # FlattenedTypeNameParser
│       ├── rewriter/             # GenericSchemaRewriter
│       ├── customizer/           # GenericResponseCustomizer (OpenApiCustomizer)
│       ├── detector/             # GenericWrapperDetector (classpath scan + auto-detect)
│       ├── properties/           # GenericResponseProperties (@ConfigurationProperties)
│       └── autoconfigure/       # GenericResponseAutoConfiguration
└── sample/                       # Sample Spring Boot app
```

## Running the sample

```bash
./gradlew :sample:bootRun
# OpenAPI spec: http://localhost:8080/v3/api-docs
# Swagger UI:   http://localhost:8080/swagger-ui.html
```

## Running tests

```bash
./gradlew :lib:test
```
