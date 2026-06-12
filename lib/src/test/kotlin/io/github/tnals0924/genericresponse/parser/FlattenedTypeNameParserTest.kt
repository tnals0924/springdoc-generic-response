package io.github.tnals0924.genericresponse.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlattenedTypeNameParserTest {

    private val parser = FlattenedTypeNameParser(
        listOf("CommonResponse", "CursorSliceResponse", "PageWrapper")
    )

    // ---------- parse ----------

    @Test
    fun `parse - simple 1-level`() {
        assertEquals(
            ParseResult.Generic("CommonResponse", ParseResult.Leaf("TestResponse")),
            parser.parse("CommonResponseTestResponse")
        )
    }

    @Test
    fun `parse - nested 2-level`() {
        assertEquals(
            ParseResult.Generic(
                "CommonResponse",
                ParseResult.Generic("CursorSliceResponse", ParseResult.Leaf("ProjectSummaryResponse"))
            ),
            parser.parse("CommonResponseCursorSliceResponseProjectSummaryResponse")
        )
    }

    @Test
    fun `parse - nested 3-level`() {
        assertEquals(
            ParseResult.Generic(
                "CommonResponse",
                ParseResult.Generic(
                    "CursorSliceResponse",
                    ParseResult.Generic("PageWrapper", ParseResult.Leaf("ProjectSummaryResponse"))
                )
            ),
            parser.parse("CommonResponseCursorSliceResponsePageWrapperProjectSummaryResponse")
        )
    }

    @Test
    fun `parse - unknown type becomes leaf`() {
        assertEquals(ParseResult.Leaf("TestResponse"), parser.parse("TestResponse"))
    }

    @Test
    fun `parse - exact base name becomes leaf (not a flat name)`() {
        assertEquals(ParseResult.Leaf("CommonResponse"), parser.parse("CommonResponse"))
    }

    @Test
    fun `parse - longest match wins over shorter prefix`() {
        // "CursorSlice" vs "CursorSliceResponse" — longer wins
        val ambiguous = FlattenedTypeNameParser(listOf("Cursor", "CursorSlice"))
        assertEquals(
            ParseResult.Generic("CursorSlice", ParseResult.Leaf("Response")),
            ambiguous.parse("CursorSliceResponse")
        )
    }

    // ---------- isGenericFlatName ----------

    @Test
    fun `isGenericFlatName - returns true for flat names`() {
        assertTrue(parser.isGenericFlatName("CommonResponseTestResponse"))
        assertTrue(parser.isGenericFlatName("CursorSliceResponseProjectSummaryResponse"))
        assertTrue(parser.isGenericFlatName("PageWrapperFoo"))
    }

    @Test
    fun `isGenericFlatName - returns false for base names`() {
        assertFalse(parser.isGenericFlatName("CommonResponse"))
        assertFalse(parser.isGenericFlatName("CursorSliceResponse"))
    }

    @Test
    fun `isGenericFlatName - returns false for unknown types`() {
        assertFalse(parser.isGenericFlatName("TestResponse"))
        assertFalse(parser.isGenericFlatName("Object"))
    }

    // ---------- toTypeArgString ----------

    @Test
    fun `toTypeArgString - leaf`() {
        assertEquals("TestResponse", ParseResult.Leaf("TestResponse").toTypeArgString())
    }

    @Test
    fun `toTypeArgString - 1-level generic`() {
        val result = ParseResult.Generic("CommonResponse", ParseResult.Leaf("TestResponse"))
        assertEquals("CommonResponse<TestResponse>", result.toTypeArgString())
    }

    @Test
    fun `toTypeArgString - 2-level nested`() {
        val result = ParseResult.Generic(
            "CommonResponse",
            ParseResult.Generic("CursorSliceResponse", ParseResult.Leaf("ProjectSummaryResponse"))
        )
        assertEquals("CommonResponse<CursorSliceResponse<ProjectSummaryResponse>>", result.toTypeArgString())
    }

    @Test
    fun `toTypeArgString - typeArg only for nested (x-generic-type-arg value)`() {
        val result = parser.parse("CommonResponseCursorSliceResponseProjectSummaryResponse")
        assertEquals(
            "CursorSliceResponse<ProjectSummaryResponse>",
            (result as ParseResult.Generic).typeArg.toTypeArgString()
        )
    }

    @Test
    fun `toTypeArgString - 3-level typeArg`() {
        val result = parser.parse("CommonResponseCursorSliceResponsePageWrapperProjectSummaryResponse")
        assertEquals(
            "CursorSliceResponse<PageWrapper<ProjectSummaryResponse>>",
            (result as ParseResult.Generic).typeArg.toTypeArgString()
        )
    }
}
