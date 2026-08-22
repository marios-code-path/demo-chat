package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MembershipPersistence
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Redis-backed [MembershipPersistence].
 *
 * [TopicMembership.key] is a raw id (not a [Key] wrapper), so the Redis key
 * is `chat:member:<rawId>` and the Set index (`chat:idx:member`) holds raw
 * id strings.
 *
 * Known limitation (documented decision): add() is SET + SADD and rem() is
 * DEL + SREM — two round trips with no MULTI/EXEC or Lua atomicity. A
 * failure between them leaves either an orphaned record (absent from the
 * index, invisible to all() — silent data loss) or a dangling index id
 * (benign: all()'s flatMap drops the empty get). Accepted at demo scale;
 * make it atomic with a Lua script if durability matters.
 */
class MembershipPersistenceRedis<T>(
    private val keyService: IKeyService<T>,
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val prefix: String = "chat:member:",
    private val indexKey: String = "chat:idx:member",
) : MembershipPersistence<T> {

    override fun key(): Mono<out Key<T>> = keyService.key(TopicMembership::class.java)

    override fun add(ent: TopicMembership<T>): Mono<Void> {
        val redisKey = prefix + ent.key.toString()
        return Mono.fromCallable { objectMapper.writeValueAsString(ent) }
            .flatMap { json ->
                stringTemplate
                    .opsForValue()
                    .set(redisKey, json)
                    .then(stringTemplate.opsForSet().add(indexKey, ent.key.toString()))
                    .then()
            }
    }

    override fun get(key: Key<T>): Mono<out TopicMembership<T>> =
        stringTemplate
            .opsForValue()
            .get(prefix + key.id.toString())
            .map { json -> objectMapper.readValue(json, TopicMembership::class.java) as TopicMembership<T> }

    override fun rem(key: Key<T>): Mono<Void> =
        stringTemplate
            .delete(prefix + key.id.toString())
            .then(stringTemplate.opsForSet().remove(indexKey, key.id.toString()))
            .then()

    override fun all(): Flux<out TopicMembership<T>> =
        stringTemplate
            .opsForSet()
            .members(indexKey)
            .flatMap { id ->
                stringTemplate
                    .opsForValue()
                    .get(prefix + id)
                    .map { json -> objectMapper.readValue(json, TopicMembership::class.java) as TopicMembership<T> }
            }

    // N round trips (one get per id) rather than a single MGET — a
    // deliberate demo-scale choice, not an oversight.
    override fun byIds(keys: List<Key<T>>): Flux<out TopicMembership<T>> =
        Flux.fromIterable(keys).flatMap { key -> get(key) }
}