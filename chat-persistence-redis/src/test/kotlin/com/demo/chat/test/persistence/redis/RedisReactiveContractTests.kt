package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.persistence.redis.impl.KeyServiceRedis
import com.demo.chat.persistence.redis.impl.UserPersistenceRedis
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Pins the reactive assembly/subscription contract for the Redis stores:
 * fallible work (JSON serialization, id generation) must run at subscription
 * time, not assembly time — a failure must arrive as onError, never as a
 * synchronous throw out of the method, and side effects (id consumption)
 * must not occur for a Mono that is never subscribed.
 */
class RedisReactiveContractTests {

    private val stringTemplate = mock<ReactiveStringRedisTemplate>()
    private val keyService = mock<IKeyService<UUID>>()

    @Test
    fun `add defers serialization to subscription`() {
        val mapper = mock<ObjectMapper>()
        whenever(mapper.writeValueAsString(any())).thenThrow(IllegalStateException("cannot serialize"))
        val store = UserPersistenceRedis<UUID>(keyService, stringTemplate, mapper)
        val ent = User.create(Key.funKey(UUID.randomUUID()), "a", "b", "c")

        val mono = store.add(ent) // assembly must not throw

        verify(mapper, times(0)).writeValueAsString(any())
        StepVerifier.create(mono).expectError(IllegalStateException::class.java).verify()
    }

    @Test
    fun `key defers id generation to subscription`() {
        val keyGen = mock<IKeyGenerator<UUID>>()
        whenever(keyGen.nextId()).thenThrow(IllegalStateException("no ids left"))
        val service = KeyServiceRedis<UUID>(stringTemplate, keyGen)

        val mono = service.key(User::class.java) // assembly must not throw

        verify(keyGen, times(0)).nextId()
        StepVerifier.create(mono).expectError(IllegalStateException::class.java).verify()
    }
}