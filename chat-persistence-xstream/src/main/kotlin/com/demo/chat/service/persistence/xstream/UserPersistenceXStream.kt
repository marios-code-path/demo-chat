package com.demo.chat.service.persistence.xstream

import com.demo.chat.convert.Converter
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.UserPersistence
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.Optional.ofNullable

// Producer and Consumers are group together to reduce
// memory pressure from copying and network pressure
// from transmission.

// TODO Cassandra datatype shaping has leaked into upstream application code!!!
data class KeyConfigurationXStream(
        val keyStreamKey: String,
        val keyUserStreamKey: String
)

class UserPersistenceXStream<T>(private val keyConfigurationXStream: KeyConfigurationXStream,
                                private val keyService: IKeyService<T>,
                                private val userTemplate: ReactiveRedisTemplate<String, User<T>>,
                                private val recordCodec: Converter<MapRecord<String, String, String>, User<T>>,
                                private val keyRangeCodec: Converter<T, Pair<Long, Long>>
) : UserPersistence<T> {
    override fun all(): Flux<out User<T>> = userTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(keyConfigurationXStream.keyUserStreamKey))
            .map { record ->
                recordCodec.convert(record)
            }


    override fun get(key: Key<T>): Mono<out User<T>> = userTemplate
            .opsForStream<String, String>()
            .range(keyConfigurationXStream.keyUserStreamKey,
                    Range.just(keyRangeCodec.convert(key.id).first.toString()))
            .map { record ->
                User.create(
                        key,
                        ofNullable(record.value["name"]).orElse(""),
                        ofNullable(record.value["handle"]).orElse(""),
                        ofNullable(record.value["imageUri"]).orElse("")
                )
            }
            .single()

    override fun key(): Mono<out Key<T>> = keyService.key(User::class.java)

    override fun add(ent: User<T>): Mono<Void> =
            userTemplate
                    .opsForStream<String, String>()
                    .add(MapRecord
                            .create(keyConfigurationXStream.keyUserStreamKey,
                                    ObjectMapper()
                                            .convertValue(ent, Map::class.java) as MutableMap<Any, Any>))
                    .then()

    override fun rem(key: Key<T>): Mono<Void> = keyService.rem(key)
}