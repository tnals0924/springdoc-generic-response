package io.github.tnals0924.genericresponse.autoconfigure

import io.github.tnals0924.genericresponse.customizer.GenericResponseCustomizer
import io.github.tnals0924.genericresponse.detector.GenericWrapperDetector
import io.github.tnals0924.genericresponse.properties.GenericResponseProperties
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer::class)
@ConditionalOnProperty(prefix = "generic-response", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GenericResponseProperties::class)
class GenericResponseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun genericResponseCustomizer(
        properties: GenericResponseProperties,
        applicationContext: ApplicationContext
    ): OpenApiCustomizer {
        val wrappers = GenericWrapperDetector(properties, applicationContext).detect()
        return GenericResponseCustomizer(wrappers)
    }
}
