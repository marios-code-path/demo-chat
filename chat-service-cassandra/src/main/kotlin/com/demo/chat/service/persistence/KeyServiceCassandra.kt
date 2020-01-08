package com.demo.chat.service.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.cassandra.CassandraKey
import com.demo.chat.service.IKeyService
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono
import java.time.Duration


class KeyServiceCassandra<T>(private val template: ReactiveCassandraTemplate,
                             private val idFactory: () -> T) : IKeyService<T> {
    override fun rem(key: Key<T>): Mono<Void> = template
            .deleteById(CassandraKey(key.id, ""), CassandraKey::class.java)
            .then()

    override fun exists(key: Key<T>): Mono<Boolean> =
            template.exists(CassandraKey(key.id, ""), CassandraKey::class.java)

    override fun <K> key(kind: Class<K>): Mono<out Key<T>> = template
            .insert(CassandraKey(idFactory(), kind.simpleName))
            .retryBackoff(1, Duration.ofMillis(1L))
    // TODO Cassandra keyGen error states
}