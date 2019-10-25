package com.demo.chat.service.persistence

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.EventKey
import com.demo.chat.service.KeyService
import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

@Table("keys")
data class CassandraEventKey(
        @Id
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID,
        @PrimaryKeyColumn(name = "kind", type = PrimaryKeyType.CLUSTERED, ordinal = 0)
        val kind: String
) : EventKey

class KeyPersistenceCassandra(private val template: ReactiveCassandraTemplate) : KeyService {
    override fun rem(key: EventKey): Mono<Void> = template
                    .deleteById(key.id, CassandraEventKey::class.java)
                    .then()

    override fun exists(key: EventKey): Mono<Boolean> =
            template.exists(key.id, CassandraEventKey::class.java)

    override fun <T> id(kind: Class<T>): Mono<EventKey> = Mono
            .just(EventKey.create(UUIDs.timeBased()))
            .doOnNext {
                template.insert(CassandraEventKey(it.id, kind.name))
            }
            .retryBackoff(1, Duration.ofMillis(1L))
            // TODO Cassandra keyGen error states

    override fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<T> =
            id(kind).map { create(it) }
}