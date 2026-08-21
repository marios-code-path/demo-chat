package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.AuthMetaPersistence
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Redis-backed [AuthMetaPersistence].
 *
 * Entities are stored as JSON strings under `chat:auth:<id>` with a Set
 * index (`chat:idx:auth`) of key ids.
 */
class AuthMetaPersistenceRedis<T>(
    private val keyService: IKeyService<T>,
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val prefix: String = "chat:auth:",
    private val indexKey: String = "chat:idx:auth",
) : AuthMetaPersistence<T> {

    override fun key(): Mono<out Key<T>> = keyService.key(AuthMetadata::class.java)

    override fun add(ent: AuthMetadata<T>): Mono<Void> {
        val redisKey = prefix + ent.key.id.toString()
        val json = objectMapper.writeValueAsString(ent)
        return stringTemplate
            .opsForValue()
            .set(redisKey, json)
            .then(stringTemplate.opsForSet().add(indexKey, ent.key.id.toString()))
            .then()
    }

    override fun get(key: Key<T>): Mono<out AuthMetadata<T>> =
        stringTemplate
            .opsForValue()
            .get(prefix + key.id.toString())
            .map { json -> objectMapper.readValue(json, AuthMetadata::class.java) as AuthMetadata<T> }

    override fun rem(key: Key<T>): Mono<Void> =
        stringTemplate
            .delete(prefix + key.id.toString())
            .then(stringTemplate.opsForSet().remove(indexKey, key.id.toString()))
            .then()

    override fun all(): Flux<out AuthMetadata<T>> =
        stringTemplate
            .opsForSet()
            .members(indexKey)
            .flatMap { id ->
                stringTemplate
                    .opsForValue()
                    .get(prefix + id)
                    .map { json -> objectMapper.readValue(json, AuthMetadata::class.java) as AuthMetadata<T> }
            }

    override fun byIds(keys: List<Key<T>>): Flux<out AuthMetadata<T>> =
        Flux.fromIterable(keys).flatMap { key -> get(key) }
}