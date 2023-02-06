package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.CSKey
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

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
        .retryWhen(Retry.backoff(5, Duration.ofMillis(100L)))
    // TODO Cassandra keyGen error states
}