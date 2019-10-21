package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.EventKey
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

@Table("data_key")
data class CassandraEventKey(
        @PrimaryKeyColumn(name = "key", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID
) : EventKey

class KeyServiceCassandra(private val template: ReactiveCassandraTemplate) : KeyService {
    override fun rem(key: EventKey): Mono<Void> =
            template
                    .delete(CassandraEventKey(key.id))
                    .then()

    fun exists(key: EventKey): Mono<Boolean> =
            template
                    .exists(key.id, CassandraEventKey::class.java)

    override fun <T> id(kind: Class<T>): Mono<EventKey> = Mono
            .just(EventKey.create(UUIDs.timeBased()))
            .doOnNext {
                template.insert(CassandraEventKey(it.id))
            }
            .retryBackoff(1, Duration.ofMillis(1L))
            // TOTO error states
    override fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<T> =
            id(kind).map { create(it) }
}