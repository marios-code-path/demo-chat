package com.demo.chat.xstream

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Mono

// TODO: Why I need this needs explanation
data class XStreamKey<T>(
        override val id: T,
        val kind: String
) : Key<T>

/**
 * Key Services using stream as source for new ID's
 * Deleting a key only adds a record for key removal
 *
 * Downstream services should also remove a deleted key from their
 * respective stores.
 *
 * seperates id <-> interval to specific functions
 */
class KeyServiceXStream<T>(private val keyConfiguration: KeyConfiguration,
                           private val stringTemplate: ReactiveRedisTemplate<String, String>,
                           private val idTointervalPair: (T) -> Pair<Long, Long>,
                           private val intervalPairToId: (Pair<Long, Long>) -> T) : IKeyService<T> {
    override fun exists(key: Key<T>): Mono<Boolean> =
            stringTemplate
                    .opsForStream<String, String>()
                    .range(keyConfiguration.keyStreamKey, Range.just(idTointervalPair(key.id).first.toString()))
                    .singleOrEmpty()
                    .hasElement()

    override fun <V> key(kind: Class<V>): Mono<out Key<T>> =
            stringTemplate
                    .opsForStream<String, String>()
                    .add(MapRecord
                            .create(keyConfiguration.keyStreamKey, mapOf(Pair("kind", kind.simpleName), Pair("exists", true)))
                            .withId(RecordId.autoGenerate()))
                    .map {
                        val id: T = intervalPairToId(Pair(it.timestamp!!, it.sequence!!))
                        XStreamKey(id, kind.simpleName)
                    }

    override fun rem(key: Key<T>): Mono<Void> =
            stringTemplate
                    .opsForStream<String, String>()
                    .add(MapRecord
                            .create(keyConfiguration.keyStreamKey,
                                    mapOf(Pair("keyId", "${idTointervalPair(key.id).first}-${idTointervalPair(key.id).second}"),
                                            Pair("exists", "false")))
                            .withId(RecordId.autoGenerate()))
                    .then()
}