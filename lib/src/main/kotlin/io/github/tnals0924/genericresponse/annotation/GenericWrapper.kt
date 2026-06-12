package io.github.tnals0924.genericresponse.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GenericWrapper(
    val dataField: String
)
