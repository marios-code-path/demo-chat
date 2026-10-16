package com.demo.chat.domain

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import org.springframework.beans.factory.ListableBeanFactory
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * A store-side lease on one store service, such as `index:user`. See
 * `CHAT-lswjobhz` and `docs/SERVICE-LEASE.md`.
 *
 * The lease lives in the persistence backend. Implementations return a
 * [Mono]. The guard is the only place that blocks.
 */
interface ServiceLeaseStore {
    /** `redis` or `cassandra`. */
    val backendName: String

    /** The full phrase that names the space this lease covers. */
    val scope: String

    /** Returns [ClaimResult.Granted] or [ClaimResult.Denied]. */
    fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult>

    fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult>

    /** Drops the lease when this owner holds it. */
    fun release(name: String, owner: String): Mono<Void>
}

/**
 * Maps each store service bean to its lease name, through the domain
 * interface that the bean implements.
 *
 * A store service bean implements `PersistenceStore` or `IndexService`. A
 * store service bean with no mapped domain interface fails the start. So a new
 * store service cannot run without a lease name.
 */
object ServiceLeaseNames {
    private val NAMES: List<Pair<Class<*>, String>> = listOf(
        UserPersistence::class.java to "persistence:user",
        TopicPersistence::class.java to "persistence:topic",
        MessagePersistence::class.java to "persistence:message",
        MembershipPersistence::class.java to "persistence:membership",
        AuthMetaPersistence::class.java to "persistence:authmeta",
        KeyValueStore::class.java to "persistence:keyvalue",
        UserIndexService::class.java to "index:user",
        TopicIndexService::class.java to "index:topic",
        MessageIndexService::class.java to "index:message",
        MembershipIndexService::class.java to "index:membership",
        AuthMetaIndex::class.java to "index:authmeta",
        KeyValueIndexService::class.java to "index:keyvalue",
    )

    /** Every name that the mapping knows, in sorted order. */
    val ALL: List<String> = NAMES.map { it.second }.sorted()

    fun isStoreService(bean: Any): Boolean = bean is PersistenceStore<*, *> || bean is IndexService<*, *, *>

    /**
     * The store services of [beans], keyed by `<holder bean>.<method>`.
     *
     * A store service is what a `PersistenceServiceBeans` or
     * `IndexServiceBeans` holder returns. A bean that only implements
     * `PersistenceStore` or `IndexService`, such as an RSocket controller, is
     * not one. The Cassandra holders create their services on each call, so
     * the services are not beans. Each method of the holder interfaces is
     * read, so a new method with a new domain fails the start in [of].
     */
    fun servicesIn(beans: ListableBeanFactory): Map<String, Any> =
        holders(beans, PersistenceServiceBeans::class.java) + holders(beans, IndexServiceBeans::class.java)

    private fun holders(beans: ListableBeanFactory, type: Class<*>): Map<String, Any> =
        beans.getBeansOfType(type, false, false).flatMap { (name, holder) ->
            type.methods.filter { it.parameterCount == 0 }.mapNotNull { method ->
                method.invoke(holder)?.let { "$name.${method.name}" to it }
            }
        }.toMap()

    /** The names of [services], keyed by bean name, without repeats and in sorted order. */
    fun of(services: Map<String, Any>): List<String> =
        services.flatMap { (bean, service) ->
            NAMES.filter { it.first.isInstance(service) }.map { it.second }.ifEmpty {
                throw IllegalStateException(
                    "The store service bean $bean (${service.javaClass.name}) has no lease name. " +
                        "Add its domain interface to ServiceLeaseNames. See docs/SERVICE-LEASE.md."
                )
            }
        }.distinct().sorted()
}

/** Reports a store service that another process holds. */
class ServiceLeaseException(
    val name: String,
    val scope: String,
    val holder: String,
    val ttl: Duration,
) : RuntimeException(message(name, scope, holder, ttl)) {
    companion object {
        fun message(name: String, scope: String, holder: String, ttl: Duration): String =
            "The store service $name is already held in the $scope.\n" +
                "Holder: $holder\n" +
                "A store service has at most one current lease holder on a shared backend. See docs/SERVICE-LEASE.md.\n" +
                "Stop the other deployment, and confirm that its process exited. A clean stop releases the lease. " +
                "After a crash, the lease expires within ${ttl.seconds}s."
    }
}
