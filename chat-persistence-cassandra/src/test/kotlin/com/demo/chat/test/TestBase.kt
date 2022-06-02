package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertAll
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.util.*

object TestKeyService : IKeyService<UUID> {
    override fun exists(key: Key<UUID>): Mono<Boolean> = Mono.just(true)

    override fun <T> key(kind: Class<T>): Mono<out Key<UUID>> = Mono.just(Key.funKey(UUID.randomUUID()))

    override fun rem(key: Key<UUID>): Mono<Void> = Mono.never()
}

fun roomAssertions(room: MessageTopic<UUID>) {
    assertAll("Topic Assertions",
        { Assertions.assertNotNull(room) },
        { Assertions.assertNotNull(room.key.id) },
        { Assertions.assertNotNull(room.data) })
}

fun userAssertions(user: User<UUID>, handle: String?, name: String?) {
    assertAll("User Assertions",
        { Assertions.assertNotNull(user) },
        { Assertions.assertNotNull(user.key.id) },
        { Assertions.assertNotNull(user.handle) },
        { Assertions.assertEquals(handle, user.handle) },
        { Assertions.assertEquals(name, user.name) }
    )
}
