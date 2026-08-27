package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.User
import com.demo.chat.persistence.redis.impl.KeyValuePersistenceRedis
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID

/**
 * The typed accessors must work for non-String values, not just strings.
 *
 * The JSON round-trip deserializes `data` as `Any` — a `LinkedHashMap` for
 * objects — so the typed accessors re-bind it with the requested type via
 * `ObjectMapper.convertValue` instead of casting. A plain cast would throw
 * ClassCastException for any stored domain object; this test pins that
 * contract.
 */
@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisKeyValueTypedDomainTests(
    @Autowired private val keyValuePersistence: KeyValuePersistenceRedis<UUID>,
    @Autowired private val stringTemplate: ReactiveStringRedisTemplate,
) {

    @BeforeEach
    fun `flush redis`() {
        stringTemplate.delete(stringTemplate.keys("*")).block()
    }

    @Test
    fun `typedGet converts a domain object stored as data`() {
        val key = Key.funKey(UUID.randomUUID())
        val user = User.create(Key.funKey(UUID.randomUUID()), "alice", "alice", "http://img")
        keyValuePersistence.add(KeyValuePair.create(key, user)).block()

        val typed = keyValuePersistence.typedGet(key, User::class.java).block()
        Assertions.assertNotNull(typed)
        Assertions.assertEquals("alice", (typed!!.data as User<*>).name)
        Assertions.assertEquals("alice", typed.data.handle)
    }

    @Test
    fun `typedAll and typedByIds convert domain objects`() {
        val key = Key.funKey(UUID.randomUUID())
        val user = User.create(Key.funKey(UUID.randomUUID()), "bob", "bob", "http://img")
        keyValuePersistence.add(KeyValuePair.create(key, user)).block()

        val all = keyValuePersistence.typedAll(User::class.java).collectList().block()
        Assertions.assertEquals(1, all!!.size)
        Assertions.assertEquals("bob", (all[0].data as User<*>).name)

        val byIds = keyValuePersistence.typedByIds(listOf(key), User::class.java).collectList().block()
        Assertions.assertEquals(1, byIds!!.size)
        Assertions.assertEquals("bob", (byIds[0].data as User<*>).name)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}