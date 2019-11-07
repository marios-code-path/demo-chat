package com.demo.chat.xstream

import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.service.ChatUserPersistence
import com.demo.chat.service.KeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.Optional.ofNullable

// Producer and Consumers are group together to reduce
// memory pressure from copying and network pressure
// from transmission.

// TODO Cassandra datatype shaping has leaked into upstream application code!!!
data class KeyConfiguration(
        val keyStreamKey: String,
        val keyUserStreamKey: String
)

class ChatUserPersistenceXStream(private val keyConfiguration: KeyConfiguration,
                                 private val keyService: KeyService,
                                 private val userTemplate: ReactiveRedisTemplate<String, User>
) : ChatUserPersistence {
    override fun all(): Flux<out User> = userTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(keyConfiguration.keyUserStreamKey))
            .map { record ->
                User.create(
                        UserKey.create(
                                UUID.fromString(ofNullable(record.value["userId"]).orElse("")),
                                ofNullable(record.value["handle"]).orElse("")),
                        ofNullable(record.value["name"]).orElse(""),
                        ofNullable(record.value["imageUri"]).orElse("")
                )
            }


    override fun get(key: EventKey): Mono<out User> = userTemplate
            .opsForStream<String, String>()
            .range(keyConfiguration.keyUserStreamKey, Range.just(key.id.mostSignificantBits.toString()))
            .map { record ->
                User.create(
                        UserKey.create(key.id, ofNullable(record.value["handle"]).orElse("")),
                        ofNullable(record.value["name"]).orElse(""),
                        ofNullable(record.value["imageUri"]).orElse("")
                )
            }
            .single()

    override fun key(): Mono<out EventKey> = keyService.id(UserKey::class.java)

    override fun add(ent: User): Mono<Void> =
            userTemplate
                    .opsForStream<String, String>()
                    .add(MapRecord
                            .create(keyConfiguration.keyUserStreamKey,
                                    ObjectMapper()
                                            .convertValue(ent, Map::class.java) as MutableMap<Any, Any>))
                    .then()

    override fun rem(key: EventKey): Mono<Void> = keyService.rem(key)
}