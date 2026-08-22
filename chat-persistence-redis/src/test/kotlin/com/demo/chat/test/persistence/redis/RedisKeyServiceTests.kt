package com.demo.chat.test.persistence.redis

import com.demo.chat.persistence.redis.impl.KeyServiceRedis
import com.demo.chat.test.key.TestKeyServiceBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID

@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisKeyServiceTests(
    @Autowired keyService: KeyServiceRedis<UUID>,
    @Autowired private val stringTemplate: ReactiveStringRedisTemplate,
) : TestKeyServiceBase<UUID>(keyService) {

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