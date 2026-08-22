package com.demo.chat.test.persistence.redis

import com.demo.chat.persistence.redis.impl.KeyValuePersistenceRedis
import com.demo.chat.test.persistence.KeyValueStoreTestBase
import org.junit.jupiter.api.BeforeEach
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
 * Exercises the typed accessors (typedGet / typedAll / typedByIds) of the
 * Redis key-value store.
 */
@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
class RedisKeyValueTypedTests(
    @Autowired keyValuePersistence: KeyValuePersistenceRedis<UUID>,
    @Autowired private val stringTemplate: ReactiveStringRedisTemplate,
) : KeyValueStoreTestBase<UUID, Any>(
    TestUUIDKeyValuePairSupplier,
    { String::class.java },
    keyValuePersistence,
    { t -> t.key },
) {

    @BeforeEach
    fun `flush redis`() {
        stringTemplate.delete(stringTemplate.keys("*")).block()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}