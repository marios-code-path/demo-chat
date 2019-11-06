package com.demo.chat.xstream

import com.demo.chat.domain.EventKey
import com.demo.chat.service.KeyService
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Mono
import java.util.*

/**
 * Key Services using stream as source for new ID's
 * Deleting a key removes that ID from the stream
 *
 * Downstream services should also remove a deleted key
 */
class KeyServiceXStream(private val keyConfiguration: KeyConfiguration,
                        private val stringTemplate: ReactiveRedisTemplate<String, String>) : KeyService {
    override fun exists(key: EventKey): Mono<Boolean> =
            stringTemplate
                    .opsForStream<String, String>()
                    .range(keyConfiguration.keyStreamKey, Range.just(key.id.mostSignificantBits.toString()))
                    .singleOrEmpty()
                    .hasElement()

    override fun <T> id(kind: Class<T>): Mono<EventKey> =
            stringTemplate
                    .opsForStream<String, String>()
                    .add(MapRecord
                            .create(keyConfiguration.keyStreamKey, mapOf(Pair("kind", kind.simpleName)))
                            .withId(RecordId.autoGenerate()))
                    .map {
                        EventKey.create(UUID(it.timestamp!!, it.sequence!!))
                    }

    override fun rem(key: EventKey): Mono<Void> =
            stringTemplate
                    .opsForStream<String, String>()
                    .delete(keyConfiguration.keyStreamKey, RecordId.of(key.id.mostSignificantBits, key.id.leastSignificantBits))
                    .then()

    override fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<T> =
            id(kind).map { create(it) }
}