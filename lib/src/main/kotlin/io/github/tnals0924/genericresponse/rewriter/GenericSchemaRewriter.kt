package io.github.tnals0924.genericresponse.rewriter

import io.github.tnals0924.genericresponse.model.GenericWrapperInfo
import io.github.tnals0924.genericresponse.parser.ParseResult
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema

class GenericSchemaRewriter(
    private val wrappers: Map<String, GenericWrapperInfo>
) {

    fun rewrite(
        schemas: MutableMap<String, Schema<*>>,
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

        schemas[flatName] = rewritten
    }

    private fun ensureBaseSchema(
        schemas: MutableMap<String, Schema<*>>,
        baseName: String,
        wrapper: GenericWrapperInfo,
        flatSchema: Schema<*>
    ) {
        if (schemas.containsKey(baseName)) return

        val base = ObjectSchema()
        flatSchema.properties?.forEach { (key, value) ->
            if (key != wrapper.dataField) {
                base.addProperty(key, value)
            }
        }
        flatSchema.required?.filter { it != wrapper.dataField }?.forEach { base.addRequiredItem(it) }

        schemas[baseName] = base
    }

    private fun buildAllOfSchema(
        baseName: String,
        dataField: String,
        flatSchema: Schema<*>
    ): ComposedSchema {
        val allOf = ComposedSchema()
        allOf.addAllOfItem(Schema<Any>().apply { `$ref` = "#/components/schemas/$baseName" })

        val dataProp = flatSchema.properties?.get(dataField)
        if (dataProp != null) {
            val dataWrapper = ObjectSchema()
            dataWrapper.addProperty(dataField, dataProp)
            allOf.addAllOfItem(dataWrapper)
        }
        return allOf
    }
}
