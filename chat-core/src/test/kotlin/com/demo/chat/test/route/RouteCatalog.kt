package com.demo.chat.test.route

import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.filter.AnnotationTypeFilter

/**
 * How a route verifies one key or id of its input. See `CHAT-avduuqwp`, T4.
 */
enum class VerificationPath {
    /** `KeyVerifier.verify` in the route body. */
    VERIFY,

    /** `KeyVerifier.resolve` in the route body. */
    RESOLVE,

    /** An argument resolver, which calls `verify` or `resolve`. */
    RESOLVER,

    /** The composite service resolves the id, sites C39 to C60. */
    SERVICE,

    /** The route asks the registry itself, so no check runs first. */
    REGISTRY,

    /** The id only filters a read, such as an index query. It never reaches a store as a key. */
    QUERY,

    /** The key of the authenticated principal. A typed store returned that user. */
    PRINCIPAL,

    /**
     * A value that the discovery reported and a person classified as no key and
     * no id. [CatalogField.note] gives the reason.
     */
    NOT_IDENTITY,

    /** A later task verifies it. [CatalogEntry.note] names the task. */
    DEFERRED,
}

/** One key or id of a route input, with its domain and its verification path. */
data class CatalogField(val domain: String, val path: VerificationPath, val note: String = "")

/**
 * One handler in a route verification catalog. An empty [fields] map means the
 * discovery reported no value that needs a classification, the classification
 * `NO_IDENTITY`. The discovery reports every value that it cannot prove free
 * of identity, so an empty map is a result and not a default.
 */
data class CatalogEntry(
    val owner: String,
    val method: String,
    val signature: String,
    val fields: Map<String, CatalogField> = emptyMap(),
) {
    val noIdentity: Boolean get() = fields.isEmpty()
}

/** Builders for a catalog source. */
object Catalog {
    fun entry(owner: String, method: String, signature: String, vararg fields: Pair<String, CatalogField>) =
        CatalogEntry(owner, method, signature, fields.toMap())

    fun verify(domain: String) = CatalogField(domain, VerificationPath.VERIFY)
    fun resolve(domain: String) = CatalogField(domain, VerificationPath.RESOLVE)
    fun resolver(domain: String) = CatalogField(domain, VerificationPath.RESOLVER)
    fun service(domain: String) = CatalogField(domain, VerificationPath.SERVICE)
    fun query(domain: String) = CatalogField(domain, VerificationPath.QUERY)
    fun registry() = CatalogField("any", VerificationPath.REGISTRY)
    fun principal() = CatalogField("USER", VerificationPath.PRINCIPAL)
    fun notIdentity(reason: String) = CatalogField("none", VerificationPath.NOT_IDENTITY, reason)
    fun deferred(domain: String, note: String) = CatalogField(domain, VerificationPath.DEFERRED, note)
}

/** Compares discovered handlers with a catalog. Each problem is one line of text. */
object RouteCatalog {
    fun compare(discovered: List<DiscoveredHandler>, catalog: List<CatalogEntry>): List<String> {
        val byKey = catalog.associateBy { it.owner to it.method }
        val found = discovered.associateBy { it.owner to it.method }
        val problems = mutableListOf<String>()

        discovered.forEach { handler ->
            val entry = byKey[handler.owner to handler.method]
            if (entry == null) {
                problems += "no catalog entry: ${render(handler)}"
                return@forEach
            }
            if (entry.signature != handler.signature) {
                problems += "signature changed: ${handler.owner}.${handler.method} is (${handler.signature}), the catalog has (${entry.signature})"
            }
            (handler.identityFields - entry.fields.keys).forEach {
                problems += "unnamed field: ${handler.owner}.${handler.method} takes '$it'"
            }
            (entry.fields.keys - handler.identityFields).forEach {
                problems += "stale field: ${handler.owner}.${handler.method} names '$it', which the handler no longer takes"
            }
        }
        (byKey.keys - found.keys).forEach { (owner, method) ->
            problems += "no handler: the catalog names $owner.$method"
        }
        return problems
    }

    /** A catalog line for a new handler, with each field left for a person to classify. */
    fun render(handler: DiscoveredHandler): String =
        "${handler.route} -> entry(\"${handler.owner}\", \"${handler.method}\", \"${handler.signature}\"" +
            handler.identityFields.joinToString("") { ", \"$it\" to TODO" } + ")"

    /** The concrete classes under [basePackage] that carry [annotation], test classes excluded. */
    // The default scan evaluates @Conditional, and every controller carries
    // @ConditionalOnProperty, so it found none. This scan reads the annotation alone.
    fun classesAnnotated(basePackage: String, annotation: Class<out Annotation>): List<Class<*>> {
        val filter = AnnotationTypeFilter(annotation)
        return object : ClassPathScanningCandidateComponentProvider(false) {
            override fun isCandidateComponent(metadataReader: MetadataReader): Boolean =
                filter.match(metadataReader, metadataReaderFactory)
        }
            .findCandidateComponents(basePackage)
            .mapNotNull(BeanDefinition::getBeanClassName)
            .filterNot { it.startsWith("com.demo.chat.test") }
            .map { Class.forName(it) }
            .sortedBy { it.name }
    }
}
