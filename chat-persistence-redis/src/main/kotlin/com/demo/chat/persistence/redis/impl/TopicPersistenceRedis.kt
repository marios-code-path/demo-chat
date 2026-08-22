package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.TopicPersistence
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Redis-backed [TopicPersistence].
 *
 * Entities are stored as JSON strings under `chat:topic:<id>` with a Set
 * index (`chat:idx:topic`) of key ids.
 *
 * Known limitation (documented decision): add() is SET + SADD and rem() is
 * DEL + SREM — two round trips with no MULTI/EXEC or Lua atomicity. A
 * failure between them leaves either an orphaned record (absent from the
 * index, invisible to all() — silent data loss) or a dangling index id
 * (benign: all()'s flatMap drops the empty get). Accepted at demo scale;
 * make it atomic with a Lua script if durability matters.
 */
class TopicPersistenceRedis<T>(
    private val keyService: IKeyService<T>,
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val prefix: String = "chat:topic:",
    private val indexKey: String = "chat:idx:topic",
) : TopicPersistence<T> {

    override fun key(): Mono<out Key<T>> = keyService.key(MessageTopic::class.java)

    override fun add(ent: MessageTopic<T>): Mono<Void> {
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

    override fun get(key: Key<T>): Mono<out MessageTopic<T>> =
        stringTemplate
            .opsForValue()
            .get(prefix + key.id.toString())
            .map { json -> objectMapper.readValue(json, MessageTopic::class.java) as MessageTopic<T> }

    override fun rem(key: Key<T>): Mono<Void> =
        stringTemplate
            .delete(prefix + key.id.toString())
            .then(stringTemplate.opsForSet().remove(indexKey, key.id.toString()))
            .then()

    override fun all(): Flux<out MessageTopic<T>> =
        stringTemplate
            .opsForSet()
            .members(indexKey)
            .flatMap { id ->
                stringTemplate
                    .opsForValue()
                    .get(prefix + id)
                    .map { json -> objectMapper.readValue(json, MessageTopic::class.java) as MessageTopic<T> }
            }

    // N round trips (one get per id) rather than a single MGET — a
    // deliberate demo-scale choice, not an oversight.
    override fun byIds(keys: List<Key<T>>): Flux<out MessageTopic<T>> =
        Flux.fromIterable(keys).flatMap { key -> get(key) }
}