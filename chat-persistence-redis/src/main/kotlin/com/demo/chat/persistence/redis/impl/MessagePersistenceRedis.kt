package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MessagePersistence
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Redis-backed [MessagePersistence].
 *
 * Entities are stored as JSON strings under `chat:msg:<id>` with a Set index
 * (`chat:idx:msg`) of key ids. Generic in the message value type [V] (the
 * JSON round-trip is type-erased; typed access is the caller's concern).
 */
class MessagePersistenceRedis<T, V>(
    private val keyService: IKeyService<T>,
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val prefix: String = "chat:msg:",
    private val indexKey: String = "chat:idx:msg",
) : MessagePersistence<T, V> {

    override fun key(): Mono<out Key<T>> = keyService.key(Message::class.java)

    override fun add(ent: Message<T, V>): Mono<Void> {
        val redisKey = prefix + ent.key.id.toString()
        return Mono.fromCallable { objectMapper.writeValueAsString(ent) }
            .flatMap { json ->
                stringTemplate
                    .opsForValue()
                    .set(redisKey, json)
                    .then(stringTemplate.opsForSet().add(indexKey, ent.key.id.toString()))
                    .then()
            }
    }

    override fun get(key: Key<T>): Mono<out Message<T, V>> =
        stringTemplate
            .opsForValue()
            .get(prefix + key.id.toString())
            .map { json -> objectMapper.readValue(json, Message::class.java) as Message<T, V> }

    override fun rem(key: Key<T>): Mono<Void> =
        stringTemplate
            .delete(prefix + key.id.toString())
            .then(stringTemplate.opsForSet().remove(indexKey, key.id.toString()))
            .then()

    override fun all(): Flux<out Message<T, V>> =
        stringTemplate
            .opsForSet()
            .members(indexKey)
            .flatMap { id ->
                stringTemplate
                    .opsForValue()
                    .get(prefix + id)
                    .map { json -> objectMapper.readValue(json, Message::class.java) as Message<T, V> }
            }

    override fun byIds(keys: List<Key<T>>): Flux<out Message<T, V>> =
        Flux.fromIterable(keys).flatMap { key -> get(key) }
}