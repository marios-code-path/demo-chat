package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.persistence.cassandra.domain.CSKey
import com.demo.chat.persistence.cassandra.domain.CSKeyValuePair
import com.demo.chat.persistence.cassandra.domain.KVKey
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.KeyValueStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

class KeyValuePersistenceCassandra<T>(
    private val template: ReactiveCassandraTemplate,
    private val repo: KeyValuePairRepository<T>,
    private val mapper: ObjectMapper,
    private val keyGen: IKeyGenerator<T>,
) : KeyValueStore<T, Any> {

    override fun key(): Mono<out Key<T>> = template
        .insert(CSKey(keyGen.nextId(), KeyValuePair::class.java.simpleName))
        .retryWhen(Retry.backoff(5, Duration.ofMillis(100L)))

    override fun all(): Flux<out KeyValuePair<T, Any>> = repo.findAll()

    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>> = repo.findAll()
        .map { kv ->
            val obj = mapper.readValue(kv.data, typeArgument)
            KeyValuePair.create(kv.key, obj)
        }

    override fun get(key: Key<T>): Mono<out KeyValuePair<T, Any>> = repo.findByKeyId(key.id)
        .map { kv ->
            KeyValuePair.create(kv.key, kv.data)
        }

    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>> =
        repo.findByKeyId(key.id)
            .map { kv ->
                val obj = mapper.readValue(kv.data, typeArgument)
                KeyValuePair.create(kv.key, obj)
            }

    override fun rem(key: Key<T>): Mono<Void> =
        repo.deleteByKeyId(key.id)

    override fun add(ent: KeyValuePair<T, Any>): Mono<Void> {
        val dataString = mapper.writeValueAsString(ent.data)
        return repo
            .save(CSKeyValuePair(KVKey(ent.key.id), dataString))
            .then()
    }

    override fun byIds(keys: List<Key<T>>): Flux<out KeyValuePair<T, Any>> = typedByIds(keys, Any::class.java)

    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        repo.findAllById(ids.map { it.id })
            .map { kv ->
                val obj = mapper.readValue(kv.data, typedArgument)
                KeyValuePair.create(kv.key, obj)
            }
}