package io.github.tnals0924.genericresponse.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "generic-response")
data class GenericResponseProperties(
    val enabled: Boolean = true,
    val autoDetect: Boolean = true,
    val basePackages: List<String> = emptyList()
)
