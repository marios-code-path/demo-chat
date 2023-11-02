package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.CSKey
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.where
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

class KeyServiceCassandra<T>(
    private val template: ReactiveCassandraTemplate,
    private val keyGen: IKeyGenerator<T>,
) : IKeyService<T> {

    override fun kind(key: Key<T>): Mono<String> =
        template.selectOne(
            Query.query(where("id").`is`(key.id)),
            CSKey::class.java
        )
            .map {
                it.kind
            }

    override fun rem(key: Key<T>): Mono<Void> = template
        .deleteById(CSKey(key.id, ""), CSKey::class.java)
        .then()

    override fun exists(key: Key<T>): Mono<Boolean> =
        template.exists(CSKey(key.id, ""), CSKey::class.java)

    override fun <S> key(kind: Class<S>): Mono<out Key<T>> = template
        .insert(CSKey(keyGen.nextId(), kind.simpleName))
        .retryWhen(Retry.backoff(5, Duration.ofMillis(100L)))
    // TODO Cassandra keyGen error states
}