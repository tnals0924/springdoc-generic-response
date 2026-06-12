package io.github.tnals0924.genericresponse.model

data class GenericWrapperInfo(
    val schemaName: String,
    val dataField: String,
    val sourceClass: Class<*>? = null
)
