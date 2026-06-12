package io.github.tnals0924.genericresponse.customizer

import io.github.tnals0924.genericresponse.model.GenericWrapperInfo
import io.github.tnals0924.genericresponse.parser.FlattenedTypeNameParser
import io.github.tnals0924.genericresponse.parser.ParseResult
import io.github.tnals0924.genericresponse.rewriter.GenericSchemaRewriter
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OpenApiCustomizer

class GenericResponseCustomizer(
    wrapperInfos: List<GenericWrapperInfo>
) : OpenApiCustomizer {

    private val parser = FlattenedTypeNameParser(wrapperInfos.map { it.schemaName })
    private val rewriter = GenericSchemaRewriter(wrapperInfos.associateBy { it.schemaName })

    @Suppress("UNCHECKED_CAST")
    override fun customise(openApi: OpenAPI) {
        val schemas = openApi.components?.schemas as? MutableMap<String, Schema<Any>> ?: return

        schemas.keys.toList()
            .filter { parser.isGenericFlatName(it) }
            .forEach { flatName ->
                val result = parser.parse(flatName)
                if (result is ParseResult.Generic) {
                    rewriter.rewrite(schemas, flatName, result)
                }
            }
    }
}
