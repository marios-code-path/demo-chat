package com.demo.chat.xstream

import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.service.ChatUserPersistence
import com.demo.chat.service.KeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

data class KeyConfiguration(
        val keyStreamKey: String,
        val keyUserStreamKey: String
)

class ChatUserPersistenceXStream(private val keyConfiguration: KeyConfiguration,
                                 private val keyService: KeyService,
                                 private val userTemplate: ReactiveRedisTemplate<String, User>
) : ChatUserPersistence<User, UserKey> {
    override fun key(handle: String): Mono<out UserKey> =
            keyService
                    .key(UserKey::class.java) {
                        UserKey.create(it.id, handle)
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