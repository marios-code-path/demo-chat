package com.demo.chat.xstream

import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.service.ChatUserPersistence
import com.demo.chat.service.KeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.time.Instant
import java.util.*
import java.util.Optional.of
import java.util.Optional.ofNullable

// Producer and Consumers are group together to reduce
// memory pressure from copying and network pressure
// from transmission.

data class KeyConfiguration(
        val keyStreamKey: String,
        val keyUserStreamKey: String
)

class ChatUserPersistenceXStream(private val keyConfiguration: KeyConfiguration,
                                 private val keyService: KeyService,
                                 private val userTemplate: ReactiveRedisTemplate<String, User>
) : ChatUserPersistence {
    override fun add(o: UserKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun all(): Flux<out User> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun get(key: UserKey): Mono<out User> = userTemplate
            .opsForStream<String, String>()
            .range(keyConfiguration.keyUserStreamKey, Range.just(key.id.mostSignificantBits.toString()))
            .map {
                User.create(key,
                        ofNullable(it.value["name"]).orElse(""),
                        ofNullable(it.value["imageUri"]).orElse(""))
            }
            .single()

    override fun key(): Mono<out EventKey> =
            keyService
                    .key(UserKey::class.java) {
                        EventKey.create(it.id)
                    }

    override fun add(key: UserKey, name: String, imgUri: String): Mono<Void> =
            userTemplate
                    .opsForStream<UserKey, User>()
                    .add(MapRecord
                            .create(keyConfiguration.keyUserStreamKey, ObjectMapper().convertValue(User.create(
                                    key, name, imgUri), Map::class.java)))
                    .then()

    override fun rem(key: UserKey): Mono<Void> = keyService.rem(EventKey.create(key.id))
}