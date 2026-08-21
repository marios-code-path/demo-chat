package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.UserPersistence
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Redis-backed [UserPersistence].
 *
 * Design choice — JSON String vs Hash fields: the whole entity is stored as a
 * JSON string under `chat:user:<id>`, with a Set index (`chat:idx:user`) of
 * key ids for `all()` / `byIds()`.
 */
class UserPersistenceRedis<T>(
    private val keyService: IKeyService<T>,
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val prefix: String = "chat:user:",
    private val indexKey: String = "chat:idx:user",
) : UserPersistence<T> {

    override fun key(): Mono<out Key<T>> = keyService.key(User::class.java)

    override fun add(ent: User<T>): Mono<Void> {
        val redisKey = prefix + ent.key.id.toString()
        val json = objectMapper.writeValueAsString(ent)
        return stringTemplate
            .opsForValue()
            .set(redisKey, json)
            .then(stringTemplate.opsForSet().add(indexKey, ent.key.id.toString()))
            .then()
    }

    override fun get(key: Key<T>): Mono<out User<T>> =
        stringTemplate
            .opsForValue()
            .get(prefix + key.id.toString())
            .map { json -> objectMapper.readValue(json, User::class.java) as User<T> }

    override fun rem(key: Key<T>): Mono<Void> =
        stringTemplate
            .delete(prefix + key.id.toString())
            .then(stringTemplate.opsForSet().remove(indexKey, key.id.toString()))
            .then()

    override fun all(): Flux<out User<T>> =
        stringTemplate
            .opsForSet()
            .members(indexKey)
            .flatMap { id ->
                stringTemplate
                    .opsForValue()
                    .get(prefix + id)
                    .map { json -> objectMapper.readValue(json, User::class.java) as User<T> }
            }

    override fun byIds(keys: List<Key<T>>): Flux<out User<T>> =
        Flux.fromIterable(keys).flatMap { key -> get(key) }
}