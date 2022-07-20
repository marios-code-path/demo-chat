package com.demo.chat.service.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.cassandra.CSKey
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.IKeyService
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.function.Supplier

class KeyServiceCassandra<T>(
        private val template: ReactiveCassandraTemplate,
        private val keyGen: IKeyGenerator<T>,
) : IKeyService<T> {
    override fun rem(key: Key<T>): Mono<Void> = template
            .deleteById(CSKey(key.id, ""), CSKey::class.java)
            .then()

    override fun exists(key: Key<T>): Mono<Boolean> =
            template.exists(CSKey(key.id, ""), CSKey::class.java)

    override fun <K> key(kind: Class<K>): Mono<out Key<T>> = template
            .insert(CSKey(keyGen.nextKey(), kind.simpleName))
            .retryBackoff(1, Duration.ofMillis(1L))
    // TODO Cassandra keyGen error states
}