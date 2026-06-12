package io.github.tnals0924.genericresponse.parser

sealed class ParseResult {
    data class Generic(val base: String, val typeArg: ParseResult) : ParseResult()
    data class Leaf(val typeName: String) : ParseResult()

    fun toTypeArgString(): String = when (this) {
        is Generic -> "$base<${typeArg.toTypeArgString()}>"
        is Leaf -> typeName
    }
}

class FlattenedTypeNameParser(knownGenerics: Collection<String>) {

    // longest match first to avoid prefix ambiguity
    private val sortedGenerics = knownGenerics.sortedByDescending { it.length }

    fun parse(typeName: String): ParseResult {
        for (prefix in sortedGenerics) {
            if (typeName.startsWith(prefix) && typeName.length > prefix.length) {
                return ParseResult.Generic(prefix, parse(typeName.removePrefix(prefix)))
            }
        }
        return ParseResult.Leaf(typeName)
    }

    fun isGenericFlatName(typeName: String): Boolean =
        sortedGenerics.any { typeName.startsWith(it) && typeName.length > it.length }
}
