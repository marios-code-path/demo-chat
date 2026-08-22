package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyValueStore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Redis-backed [KeyValueStore].
 *
 * Pairs are stored as JSON strings under `chat:kv:<id>` with a Set index
 * (`chat:idx:kv`) of key ids. The typed accessors re-bind the deserialized
 * `data` (an `Any` — a `LinkedHashMap` for objects after the JSON round
 * trip) to the requested type via `ObjectMapper.convertValue`, so they work
 * for domain objects, not just strings.
 *
 * Known limitation (documented decision): add() is SET + SADD and rem() is
 * DEL + SREM — two round trips with no MULTI/EXEC or Lua atomicity. A
 * failure between them leaves either an orphaned record (absent from the
 * index, invisible to all() — silent data loss) or a dangling index id
 * (benign: all()'s flatMap drops the empty get). Accepted at demo scale;
 * make it atomic with a Lua script if durability matters.
 */
class KeyValuePersistenceRedis<T>(
    private val keyService: IKeyService<T>,
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val prefix: String = "chat:kv:",
    private val indexKey: String = "chat:idx:kv",
) : KeyValueStore<T, Any> {

    override fun key(): Mono<out Key<T>> = keyService.key(KeyValuePair::class.java)

    override fun add(ent: KeyValuePair<T, Any>): Mono<Void> {
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

    override fun get(key: Key<T>): Mono<out KeyValuePair<T, Any>> =
        stringTemplate
            .opsForValue()
            .get(prefix + key.id.toString())
            .map { json -> objectMapper.readValue(json, KeyValuePair::class.java) as KeyValuePair<T, Any> }

    override fun rem(key: Key<T>): Mono<Void> =
        stringTemplate
            .delete(prefix + key.id.toString())
            .then(stringTemplate.opsForSet().remove(indexKey, key.id.toString()))
            .then()

    override fun all(): Flux<out KeyValuePair<T, Any>> =
        stringTemplate
            .opsForSet()
            .members(indexKey)
            .flatMap { id ->
                stringTemplate
                    .opsForValue()
                    .get(prefix + id)
                    .map { json -> objectMapper.readValue(json, KeyValuePair::class.java) as KeyValuePair<T, Any> }
            }

    // N round trips (one get per id) rather than a single MGET — a
    // deliberate demo-scale choice, not an oversight.
    override fun byIds(keys: List<Key<T>>): Flux<out KeyValuePair<T, Any>> =
        Flux.fromIterable(keys).flatMap { key -> get(key) }

    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>> =
        get(key).map { kv -> KeyValuePair.create(kv.key, rebind(kv.data, typeArgument)) }

    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        all().map { kv -> KeyValuePair.create(kv.key, rebind(kv.data, typeArgument)) }

    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        byIds(ids).map { kv -> KeyValuePair.create(kv.key, rebind(kv.data, typedArgument)) }

    /**
     * Re-binds the untyped `data` of a stored pair to [type].
     *
     * The round trip is asymmetric for polymorphic domain types, which is why
     * a plain `convertValue` is not enough. Serialising a `User` on its own
     * writes the type wrapper its `@JsonTypeInfo(WRAPPER_OBJECT)` declares:
     *
     *     {"user":{"name":"alice",...}}
     *
     * but as the `data` of a KeyValuePair it is written through the erased
     * type parameter `E`, so no wrapper is emitted:
     *
     *     {"keyValue":{"key":{...},"data":{"name":"alice",...}}}
     *
     * Reading back gives a flat Map. Handing that to `convertValue` makes
     * Jackson read the first property name as a type id and fail with
     * "Could not resolve type id 'name' as a subtype of User".
     *
     * So when the target type declares a WRAPPER_OBJECT type name and the
     * stored value does not already carry it, put it back before converting.
     * Types without polymorphic annotations - String, Int, plain data classes -
     * convert directly, unchanged.
     *
     * This is a workaround, not the fix. The cause is that the domain
     * interfaces carry @JsonTypeInfo uniformly while chat-core also registers
     * custom deserializers for the same types; for User the wrapper feeds
     * nothing but Jackson's own type machinery. Removing it makes this method
     * unnecessary - verified - but changes the REST wire format, which
     * LongUserRestTests pins at $.user.name. Tracked as CHAT-gjggodpa; delete
     * this helper when that lands.
     */
    private fun <E> rebind(data: Any?, type: Class<E>): E {
        val typeInfo = type.getAnnotation(JsonTypeInfo::class.java)
        val typeName = type.getAnnotation(JsonTypeName::class.java)?.value
        val needsWrapper = typeInfo != null &&
            typeInfo.include == JsonTypeInfo.As.WRAPPER_OBJECT &&
            typeName != null &&
            data is Map<*, *> &&
            !data.containsKey(typeName)

        return objectMapper.convertValue(
            if (needsWrapper) mapOf(typeName to data) else data,
            type,
        )
    }
}