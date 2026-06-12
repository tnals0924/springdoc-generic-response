package io.github.tnals0924.genericresponse.rewriter

import io.github.tnals0924.genericresponse.model.GenericWrapperInfo
import io.github.tnals0924.genericresponse.parser.ParseResult
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema

class GenericSchemaRewriter(
    private val wrappers: Map<String, GenericWrapperInfo>
) {

    @Suppress("UNCHECKED_CAST")
    fun rewrite(
        schemas: MutableMap<String, Schema<Any>>,
        flatName: String,
        parseResult: ParseResult.Generic
    ) {
        val baseName = parseResult.base
        val wrapper = wrappers[baseName] ?: return
        val flatSchema = schemas[flatName] ?: return

        ensureBaseSchema(schemas, baseName, wrapper, flatSchema)

        val rewritten = buildAllOfSchema(baseName, wrapper.dataField, flatSchema)
        rewritten.addExtension("x-generic-base", baseName)
        rewritten.addExtension("x-generic-type-arg", parseResult.typeArg.toTypeArgString())

        schemas[flatName] = rewritten as Schema<Any>
    }

    private fun ensureBaseSchema(
        schemas: MutableMap<String, Schema<Any>>,
        baseName: String,
        wrapper: GenericWrapperInfo,
        flatSchema: Schema<Any>
    ) {
        if (schemas.containsKey(baseName)) return

        val base = ObjectSchema()
        flatSchema.properties?.forEach { (key, value) ->
            if (key != wrapper.dataField) {
                @Suppress("UNCHECKED_CAST")
                base.addProperty(key, value as Schema<Any>)
            }
        }
        flatSchema.required?.filter { it != wrapper.dataField }?.forEach { base.addRequiredItem(it) }

        @Suppress("UNCHECKED_CAST")
        schemas[baseName] = base as Schema<Any>
    }

    private fun buildAllOfSchema(
        baseName: String,
        dataField: String,
        flatSchema: Schema<Any>
    ): ComposedSchema {
        val allOf = ComposedSchema()
        allOf.addAllOfItem(Schema<Any>().apply { `$ref` = "#/components/schemas/$baseName" })

        val dataProp = flatSchema.properties?.get(dataField)
        if (dataProp != null) {
            val dataWrapper = ObjectSchema()
            @Suppress("UNCHECKED_CAST")
            dataWrapper.addProperty(dataField, dataProp as Schema<Any>)
            allOf.addAllOfItem(dataWrapper)
        }
        return allOf
    }
}
