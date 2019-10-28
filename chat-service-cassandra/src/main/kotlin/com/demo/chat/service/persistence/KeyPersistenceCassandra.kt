package com.demo.chat.service.persistence

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.EventKey
import com.demo.chat.domain.UserKey
import com.demo.chat.service.KeyService
import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

@Table("keys")
data class CassandraEventKey(
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID,
        val kind: String
) : EventKey

interface EventKeyRepository : ReactiveCassandraRepository<CassandraEventKey, UUID>

class KeyPersistenceCassandra(private val template: ReactiveCassandraTemplate) : KeyService {
    override fun rem(key: EventKey): Mono<Void> = template
                    .deleteById(CassandraEventKey(key.id, CassandraEventKey::class.simpleName!!), CassandraEventKey::class.java)
                    .then()

    override fun exists(key: EventKey): Mono<Boolean> =
            template.exists(CassandraEventKey(key.id, CassandraEventKey::class.simpleName!!), CassandraEventKey::class.java)

    override fun <T> id(kind: Class<T>): Mono<EventKey> = template
                    .insert(CassandraEventKey(UUIDs.timeBased(), kind.simpleName))
                    .map {
                        EventKey.create(it.id)
                    }
                    .retryBackoff(1, Duration.ofMillis(1L))
            // TODO Cassandra keyGen error states

    override fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<T> =
            id(kind).map { create(it) }
}