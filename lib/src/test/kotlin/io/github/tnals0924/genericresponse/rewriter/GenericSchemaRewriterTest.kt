@file:Suppress("UNCHECKED_CAST")

package io.github.tnals0924.genericresponse.rewriter

import io.github.tnals0924.genericresponse.model.GenericWrapperInfo
import io.github.tnals0924.genericresponse.parser.FlattenedTypeNameParser
import io.github.tnals0924.genericresponse.parser.ParseResult
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GenericSchemaRewriterTest {

    private val wrappers = mapOf(
        "CommonResponse" to GenericWrapperInfo("CommonResponse", "data"),
        "CursorSliceResponse" to GenericWrapperInfo("CursorSliceResponse", "content"),
        "PageWrapper" to GenericWrapperInfo("PageWrapper", "items")
    )
    private val parser = FlattenedTypeNameParser(wrappers.keys)
    private val rewriter = GenericSchemaRewriter(wrappers)

    // ---------- helpers ----------

    private fun schema(block: ObjectSchema.() -> Unit = {}): Schema<Any> =
        ObjectSchema().apply(block) as Schema<Any>

    private fun ref(ref: String): Schema<Any> =
        Schema<Any>().apply { `$ref` = ref }

    private fun flatSchemas(vararg pairs: Pair<String, Schema<Any>>): MutableMap<String, Schema<Any>> =
        mutableMapOf(*pairs)

    // ---------- allOf structure ----------

    @Test
    fun `rewrites flat schema to allOf with ref and data property`() {
        val schemas = flatSchemas(
            "CommonResponseTestResponse" to schema {
                addProperty("status", schema { type = "integer" })
                addProperty("code", schema { type = "string" })
                addProperty("data", ref("#/components/schemas/TestResponse"))
            }
        )
        rewrite(schemas, "CommonResponseTestResponse")

        val rewritten = schemas["CommonResponseTestResponse"]!!
        assertNotNull(rewritten.allOf)
        assertEquals(2, rewritten.allOf!!.size)
        assertEquals("#/components/schemas/CommonResponse", rewritten.allOf!![0].`$ref`)
        assertNotNull(rewritten.allOf!![1].properties?.get("data"))
    }

    @Test
    fun `allOf second item carries data property from original schema`() {
        val schemas = flatSchemas(
            "CommonResponseTestResponse" to schema {
                addProperty("data", ref("#/components/schemas/TestResponse"))
            }
        )
        rewrite(schemas, "CommonResponseTestResponse")

        val dataInAllOf = schemas["CommonResponseTestResponse"]!!
            .allOf!![1].properties!!["data"]!!
        assertEquals("#/components/schemas/TestResponse", dataInAllOf.`$ref`)
    }

    // ---------- base schema creation ----------

    @Test
    fun `creates base schema from non-data fields of flat schema`() {
        val schemas = flatSchemas(
            "CommonResponseTestResponse" to schema {
                addProperty("status", schema { type = "integer" })
                addProperty("code", schema { type = "string" })
                addProperty("data", ref("#/components/schemas/TestResponse"))
            }
        )
        rewrite(schemas, "CommonResponseTestResponse")

        val base = schemas["CommonResponse"]!!
        assertTrue(base.properties!!.containsKey("status"))
        assertTrue(base.properties!!.containsKey("code"))
        assertFalse(base.properties!!.containsKey("data"))
    }

    @Test
    fun `does not overwrite existing base schema`() {
        val existingBase = schema { addProperty("custom", schema { type = "string" }) }
        val schemas = flatSchemas(
            "CommonResponse" to existingBase,
            "CommonResponseTestResponse" to schema {
                addProperty("data", ref("#/components/schemas/TestResponse"))
            }
        )
        rewrite(schemas, "CommonResponseTestResponse")

        assertSame(existingBase, schemas["CommonResponse"])
    }

    @Test
    fun `base schema excludes required item for data field`() {
        val schemas = flatSchemas(
            "CommonResponseTestResponse" to schema {
                addProperty("status", schema { type = "integer" })
                addProperty("data", ref("#/components/schemas/TestResponse"))
                addRequiredItem("status")
                addRequiredItem("data")
            }
        )
        rewrite(schemas, "CommonResponseTestResponse")

        val base = schemas["CommonResponse"]!!
        assertTrue(base.required!!.contains("status"))
        assertFalse(base.required!!.contains("data"))
    }

    // ---------- x-extension ----------

    @Test
    fun `sets x-generic-base to base type name`() {
        val schemas = flatSchemas(
            "CommonResponseTestResponse" to schema {
                addProperty("data", ref("#/components/schemas/TestResponse"))
            }
        )
        rewrite(schemas, "CommonResponseTestResponse")

        assertEquals("CommonResponse", schemas["CommonResponseTestResponse"]!!.extensions!!["x-generic-base"])
    }

    @Test
    fun `sets x-generic-type-arg to leaf type for 1-level`() {
        val schemas = flatSchemas(
            "CommonResponseTestResponse" to schema {
                addProperty("data", ref("#/components/schemas/TestResponse"))
            }
        )
        rewrite(schemas, "CommonResponseTestResponse")

        assertEquals("TestResponse", schemas["CommonResponseTestResponse"]!!.extensions!!["x-generic-type-arg"])
    }

    @Test
    fun `sets x-generic-type-arg with nested expression for 2-level`() {
        val schemas = flatSchemas(
            "CommonResponseCursorSliceResponseProjectSummaryResponse" to schema {
                addProperty("data", ref("#/components/schemas/CursorSliceResponseProjectSummaryResponse"))
            }
        )
        rewrite(schemas, "CommonResponseCursorSliceResponseProjectSummaryResponse")

        assertEquals(
            "CursorSliceResponse<ProjectSummaryResponse>",
            schemas["CommonResponseCursorSliceResponseProjectSummaryResponse"]!!
                .extensions!!["x-generic-type-arg"]
        )
    }

    @Test
    fun `sets x-generic-type-arg with nested expression for 3-level`() {
        val schemas = flatSchemas(
            "CommonResponseCursorSliceResponsePageWrapperProjectSummaryResponse" to schema {
                addProperty("data", ref("#/components/schemas/CursorSliceResponsePageWrapperProjectSummaryResponse"))
            }
        )
        rewrite(schemas, "CommonResponseCursorSliceResponsePageWrapperProjectSummaryResponse")

        assertEquals(
            "CursorSliceResponse<PageWrapper<ProjectSummaryResponse>>",
            schemas["CommonResponseCursorSliceResponsePageWrapperProjectSummaryResponse"]!!
                .extensions!!["x-generic-type-arg"]
        )
    }

    // ---------- edge cases ----------

    @Test
    fun `void response - data field absent produces allOf with ref only`() {
        val schemas = flatSchemas(
            "CommonResponseObject" to schema {
                addProperty("status", schema { type = "integer" })
                // no "data" property
            }
        )
        rewrite(schemas, "CommonResponseObject")

        val rewritten = schemas["CommonResponseObject"]!!
        assertNotNull(rewritten.allOf)
        assertEquals(1, rewritten.allOf!!.size)
        assertEquals("#/components/schemas/CommonResponse", rewritten.allOf!![0].`$ref`)
        assertEquals("CommonResponse", rewritten.extensions!!["x-generic-base"])
        assertEquals("Object", rewritten.extensions!!["x-generic-type-arg"])
    }

    @Test
    fun `void response - data field is inline object schema`() {
        val schemas = flatSchemas(
            "CommonResponseObject" to schema {
                addProperty("data", schema { type = "object" }) // inline {type:object}
            }
        )
        rewrite(schemas, "CommonResponseObject")

        val rewritten = schemas["CommonResponseObject"]!!
        // allOf has 2 items: ref + inline data
        assertEquals(2, rewritten.allOf!!.size)
        assertEquals("object", rewritten.allOf!![1].properties!!["data"]!!.type)
    }

    @Test
    fun `skips rewrite if wrapper info not found for base`() {
        val rewriterWithoutBase = GenericSchemaRewriter(emptyMap())
        val schemas = flatSchemas(
            "CommonResponseTestResponse" to schema {
                addProperty("data", ref("#/components/schemas/TestResponse"))
            }
        )
        val original = schemas["CommonResponseTestResponse"]
        val result = parser.parse("CommonResponseTestResponse") as ParseResult.Generic
        rewriterWithoutBase.rewrite(schemas, "CommonResponseTestResponse", result)

        assertSame(original, schemas["CommonResponseTestResponse"])
    }

    @Test
    fun `content field (CursorSliceResponse) is correctly identified as data field`() {
        val schemas = flatSchemas(
            "CursorSliceResponseProjectSummaryResponse" to schema {
                addProperty("size", schema { type = "integer" })
                addProperty("hasNext", schema { type = "boolean" })
                addProperty("content", schema {
                    type = "array"
                    items = ref("#/components/schemas/ProjectSummaryResponse")
                })
            }
        )
        rewrite(schemas, "CursorSliceResponseProjectSummaryResponse")

        val rewritten = schemas["CursorSliceResponseProjectSummaryResponse"]!!
        assertEquals("CursorSliceResponse", rewritten.extensions!!["x-generic-base"])
        assertEquals("ProjectSummaryResponse", rewritten.extensions!!["x-generic-type-arg"])
        assertFalse(schemas["CursorSliceResponse"]!!.properties!!.containsKey("content"))
        assertTrue(schemas["CursorSliceResponse"]!!.properties!!.containsKey("size"))
    }

    // ---------- helper ----------

    private fun rewrite(schemas: MutableMap<String, Schema<Any>>, flatName: String) {
        val result = parser.parse(flatName) as ParseResult.Generic
        rewriter.rewrite(schemas, flatName, result)
    }
}
