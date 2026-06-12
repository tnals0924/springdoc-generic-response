# springdoc-generic-response

A library that fixes the generic type flattening problem in Spring Boot + springdoc-openapi environments. When `CommonResponse<T>` gets flattened into `CommonResponseTestResponse` in the generated OpenAPI spec, this library rewrites the schema to preserve generic type information via `allOf` + `x-extension`, enabling frontend tools like swagger-typescript-api to generate proper generic types.

## Project Structure

```
springdoc-generic-response/
├── build.gradle.kts                  # Root build (shared config)
├── settings.gradle.kts
│
├── lib/                              # Published library module
│   ├── build.gradle.kts
│   └── src/main/kotlin/io/github/{yourname}/genericresponse/
│       ├── annotation/
│       │   └── GenericWrapper.kt             # @GenericWrapper annotation
│       ├── model/
│       │   └── GenericWrapperInfo.kt         # Detected generic wrapper info
│       ├── detector/
│       │   └── GenericWrapperDetector.kt     # Scans @GenericWrapper + auto-detect
│       ├── parser/
│       │   └── FlattenedTypeNameParser.kt    # Parses "CommonResponseXxx" names
│       ├── rewriter/
│       │   └── GenericSchemaRewriter.kt      # Rewrites to allOf + x-extension
│       ├── customizer/
│       │   └── GenericResponseCustomizer.kt  # OpenApiCustomizer implementation
│       ├── properties/
│       │   └── GenericResponseProperties.kt  # application.yml config binding
│       └── autoconfigure/
│           └── GenericResponseAutoConfiguration.kt
│
├── lib/src/main/resources/META-INF/spring/
│   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
└── sample/                           # Working sample Spring Boot app
    └── src/main/kotlin/sample/
        ├── SampleApplication.kt
        ├── response/
        │   ├── CommonResponse.kt     # @GenericWrapper usage example
        │   └── CursorSliceResponse.kt
        └── controller/
            └── SampleController.kt
```

## Tech Stack

- Kotlin
- Gradle (Kotlin DSL)
- Spring Boot 3.x
- springdoc-openapi 2.x (`springdoc-openapi-starter-webmvc-ui`)
- swagger-core (`io.swagger.v3.oas.models`)
- Spring Boot AutoConfiguration (`AutoConfiguration.imports`)

## Core Problem

springdoc flattens generic types into concatenated names, causing type explosion:

```yaml
# springdoc default (broken)
CommonResponseTestResponse:
  properties: { code, message, data: $ref TestResponse }

CommonResponseCursorSliceResponseProjectSummaryResponse:
  properties: { code, message, data: { content: [...], hasNext, ... } }
```

This library rewrites schemas to use `allOf` + `x-extension`:

```yaml
# after this library
CommonResponse:
  properties: { code, message }

CommonResponseTestResponse:
  allOf:
    - $ref: CommonResponse
    - properties: { data: { $ref: TestResponse } }
  x-generic-base: CommonResponse
  x-generic-type-arg: TestResponse
```

## Key Components

### @GenericWrapper

Marks a class as a generic wrapper type. The `dataField` specifies which field holds the generic parameter `T`.

```kotlin
@GenericWrapper(dataField = "data")
data class CommonResponse<T>(val status: Int, val code: String, val message: String, val data: T? = null)

@GenericWrapper(dataField = "content")
data class CursorSliceResponse<T>(val content: List<T>, val size: Int, val hasNext: Boolean, ...)
```

### FlattenedTypeNameParser

Parses flattened type names using known generic wrapper names as prefixes (longest-match, recursive):

```
Input:  "CommonResponseCursorSliceResponseProjectSummaryResponse"
Known:  ["CommonResponse", "CursorSliceResponse"]
Output: Generic(base="CommonResponse", typeArg=Generic(base="CursorSliceResponse", typeArg=Leaf("ProjectSummaryResponse")))
```

### GenericSchemaRewriter

Rewrites a flat OpenAPI Schema object into `allOf` structure and injects `x-generic-base` / `x-generic-type-arg` extensions.

### GenericResponseCustomizer

Implements `OpenApiCustomizer`. Runs after springdoc finishes spec generation, collects all `XxxResponseYyy`-pattern schemas, parses them, and rewrites them.

### GenericResponseProperties

```yaml
generic-response:
  enabled: true        # default: true
  auto-detect: true    # attempt detection without annotation (name collision risk)
  base-packages:
    - com.example.response
```

## Cases to Handle

1. `CommonResponse<XxxResponse>` — simple 1-level
2. `CommonResponse<CursorSliceResponse<XxxResponse>>` — nested 2-level
3. `CommonResponse<void>` / `CommonResponseObject` — no data ref, base only

## Frontend Integration (5 lines)

```javascript
// swagger-typescript-api generate config
hooks: {
  onParseSchema: (originalSchema, parsedSchema) => {
    if (originalSchema?.["x-generic-base"] && originalSchema?.["x-generic-type-arg"]) {
      parsedSchema.content = `${originalSchema["x-generic-base"]}<${originalSchema["x-generic-type-arg"]}>`;
    }
    return parsedSchema;
  }
}
```

## Implementation Phases

**Phase 1 — Core**
- `@GenericWrapper` annotation
- `FlattenedTypeNameParser` (1-level + nested 2-level)
- `GenericSchemaRewriter` (allOf rewrite + x-extension)
- `GenericResponseCustomizer` (OpenApiCustomizer impl)
- Verify with sample app

**Phase 2 — Completeness**
- `GenericResponseAutoConfiguration`
- `GenericResponseProperties`
- `AutoConfiguration.imports` registration
- Auto-detect fallback (no annotation)

**Phase 3 — Edge Cases**
- `CommonResponseObject` (no data ref)
- 3+ level nested generics
- Unit tests

## Why Annotation-Based

Heuristic name parsing alone cannot reliably parse nested generics. Without knowing the full set of generic wrapper names, `CommonResponseCursorSliceResponseProjectSummaryResponse` can be mis-parsed (e.g., split at the wrong boundary). The `@GenericWrapper` annotation makes the wrapper set explicit and deterministic. Auto-detect fallback is provided but carries name-collision risk.