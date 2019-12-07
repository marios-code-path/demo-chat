package com.demo.chat.service.persistence

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.CassandraKey
import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDKey
import com.demo.chat.service.KeyService
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono
import java.time.Duration


class KeyServiceCassandra(private val template: ReactiveCassandraTemplate) : KeyService {
    override fun rem(key: UUIDKey): Mono<Void> = template
            .deleteById(CassandraKey(key.id, CassandraKey::class.simpleName!!), CassandraKey::class.java)
            .then()

    override fun exists(key: UUIDKey): Mono<Boolean> =
            template.exists(CassandraKey(key.id, CassandraKey::class.simpleName!!), CassandraKey::class.java)

    override fun <T> id(kind: Class<T>): Mono<UUIDKey> = template
            .insert(CassandraKey(UUIDs.timeBased(), kind.simpleName))
            .map {
                Key.eventKey(it.id)
            }
            .retryBackoff(1, Duration.ofMillis(1L))
    // TODO Cassandra keyGen error states

    override fun <T> key(kind: Class<T>, create: (key: UUIDKey) -> T): Mono<T> =
            id(kind).map { create(it) }
}