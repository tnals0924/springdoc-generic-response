package io.github.tnals0924.genericresponse.detector

import io.github.tnals0924.genericresponse.annotation.GenericWrapper
import io.github.tnals0924.genericresponse.model.GenericWrapperInfo
import io.github.tnals0924.genericresponse.properties.GenericResponseProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.core.type.filter.TypeFilter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable

class GenericWrapperDetector(
    private val properties: GenericResponseProperties,
    private val applicationContext: ApplicationContext
) {
    private val log = LoggerFactory.getLogger(GenericWrapperDetector::class.java)

    fun detect(): List<GenericWrapperInfo> {
        val packages = resolvePackages()
        val annotated = scanAnnotated(packages)

        if (!properties.autoDetect) return annotated

        val annotatedNames = annotated.map { it.schemaName }.toSet()
        val autoDetected = autoDetect(packages).filter { it.schemaName !in annotatedNames }
        return annotated + autoDetected
    }

    private fun resolvePackages(): List<String> {
        if (properties.basePackages.isNotEmpty()) return properties.basePackages
        return runCatching {
            AutoConfigurationPackages.get(applicationContext.autowireCapableBeanFactory)
        }.getOrElse {
            log.warn("[generic-response] Could not resolve base packages; skipping scan")
            emptyList()
        }
    }

    private fun scanAnnotated(packages: List<String>): List<GenericWrapperInfo> {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(GenericWrapper::class.java))
        return packages
            .flatMap { scanner.findCandidateComponents(it) }
            .mapNotNull { beanDef ->
                val clazz = loadClass(beanDef.beanClassName) ?: return@mapNotNull null
                val ann = clazz.getAnnotation(GenericWrapper::class.java) ?: return@mapNotNull null
                GenericWrapperInfo(clazz.simpleName, ann.dataField, clazz)
            }
    }

    private fun autoDetect(packages: List<String>): List<GenericWrapperInfo> {
        if (packages.isEmpty()) return emptyList()
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(TypeFilter { _, _ -> true })
        return packages
            .flatMap { runCatching { scanner.findCandidateComponents(it) }.getOrElse { emptySet() } }
            .mapNotNull { beanDef ->
                val clazz = loadClass(beanDef.beanClassName) ?: return@mapNotNull null
                if (clazz.isAnnotationPresent(GenericWrapper::class.java)) return@mapNotNull null
                if (clazz.typeParameters.size != 1) return@mapNotNull null
                val dataField = findGenericField(clazz) ?: return@mapNotNull null
                log.debug("[generic-response] Auto-detected: ${clazz.simpleName} (dataField=$dataField)")
                GenericWrapperInfo(clazz.simpleName, dataField, clazz)
            }
    }

    // Finds the first field whose type directly uses the single generic type parameter.
    // Handles both T and List<T> / Collection<T> patterns.
    private fun findGenericField(clazz: Class<*>): String? {
        val typeParamName = clazz.typeParameters.firstOrNull()?.name ?: return null
        return clazz.declaredFields.firstOrNull { field ->
            when (val gt = field.genericType) {
                is TypeVariable<*> -> gt.name == typeParamName
                is ParameterizedType -> gt.actualTypeArguments.any {
                    it is TypeVariable<*> && it.name == typeParamName
                }
                else -> false
            }
        }?.name
    }

    private fun loadClass(name: String?): Class<*>? =
        name?.let { runCatching { Class.forName(it) }.getOrNull() }
}
