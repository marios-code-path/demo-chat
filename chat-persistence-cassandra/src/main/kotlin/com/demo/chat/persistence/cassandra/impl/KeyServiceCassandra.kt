package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyRootConflictException
import com.demo.chat.domain.RootKeyDeletionException
import com.demo.chat.domain.RootKeyRegistrationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.cassandra.domain.CSKeyRow
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.where
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

/**
 * The key registry of the Cassandra backend. Each minted id writes one
 * `keys (id, root)` row. A root key has no row. [rootOf] answers the id itself
 * for a root key. See `CHAT-avduuqwp`.
 */
class KeyServiceCassandra<T : Any>(
    private val template: ReactiveCassandraTemplate,
    private val keyGen: IKeyGenerator<T>,
    private val rootKeys: RootKeys<T>,
) : IKeyService<T> {

    /** The retry covers the insert alone, so a missing root fails at once and names itself. */
    override fun key(domain: ChatDomain): Mono<out Key<T>> = Mono
        .fromCallable { CSKeyRow(keyGen.nextId(), rootKeys.of(domain).id) }
        .flatMap { row -> template.insert(row).retryWhen(Retry.backoff(5, Duration.ofMillis(100L))) }
        .map { row -> Key.of(row.id, row.root) }

    override fun rem(key: Key<T>): Mono<Void> =
        if (rootKeys.domainOfRoot(key.id) != null) Mono.error(RootKeyDeletionException(key.id))
        else template.delete(Query.query(where("id").`is`(key.id)), CSKeyRow::class.java).then()

    override fun exists(key: Key<T>): Mono<Boolean> = rootOf(key.id).hasElement()

    override fun rootOf(id: T): Mono<T> =
        if (rootKeys.domainOfRoot(id) != null) Mono.just(id)
        else template.selectOne(Query.query(where("id").`is`(id)), CSKeyRow::class.java)
            .map { @Suppress("UNCHECKED_CAST") (it.root as T) }

    /**
     * `IF NOT EXISTS` writes the row only when the id is absent. The read
     * after it returns the stored root in both cases. Decision 2 of the spec.
     */
    @Suppress("UNCHECKED_CAST")
    override fun register(key: Key<T>): Mono<Void> =
        if (rootKeys.domainOfRoot(key.id) != null) Mono.error(RootKeyRegistrationException(key.id))
        else template.reactiveCqlOperations
            .execute("INSERT INTO keys (id, root) VALUES (?, ?) IF NOT EXISTS", key.id, key.root)
            .then(
                template.reactiveCqlOperations
                    .query("SELECT root FROM keys WHERE id = ?", { row, _ -> row.getObject("root") as T }, key.id)
                    .next()
            )
            .flatMap { stored ->
                if (stored == key.root) Mono.empty<Void>()
                else Mono.error(KeyRootConflictException(key.id, stored, key.root))
            }
}
