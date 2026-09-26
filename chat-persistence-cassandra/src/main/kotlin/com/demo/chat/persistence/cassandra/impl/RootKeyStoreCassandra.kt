package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.RootKeyStore
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono

/**
 * The root key store of the Cassandra backend. It holds the `root_keys` table
 * of the keyspace, keyed by domain wire name. A keyspace holds one key type.
 *
 * `IF NOT EXISTS` writes a root only when the domain has none. So two nodes
 * that start at once use the one root that won. `root_keys` is absent from the
 * truncate scripts. See `CHAT-avduuqwp`.
 */
class RootKeyStoreCassandra<T : Any>(private val template: ReactiveCassandraTemplate) : RootKeyStore<T> {

    @Suppress("UNCHECKED_CAST")
    override fun read(): Mono<Map<ChatDomain, T>> = template.reactiveCqlOperations
        .query("SELECT domain, id FROM root_keys") { row, _ ->
            val name = row.getString("domain")!!
            (ChatDomain.parse(name) ?: throw ChatException("The table root_keys names an unknown domain: $name")) to
                (row.getObject("id") as T)
        }
        .collectMap({ it.first }, { it.second })

    @Suppress("UNCHECKED_CAST")
    override fun createIfAbsent(domain: ChatDomain, id: T): Mono<T> = template.reactiveCqlOperations
        .execute("INSERT INTO root_keys (domain, id) VALUES (?, ?) IF NOT EXISTS", domain.wireName, id)
        .then(
            template.reactiveCqlOperations
                .query("SELECT id FROM root_keys WHERE domain = ?", { row, _ -> row.getObject("id") as T }, domain.wireName)
                .next()
        )
}
