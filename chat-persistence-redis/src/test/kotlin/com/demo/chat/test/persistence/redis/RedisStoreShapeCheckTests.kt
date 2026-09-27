package com.demo.chat.test.persistence.redis

import com.demo.chat.persistence.redis.impl.RedisStoreShapeCheck
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension

/**
 * A key registry hash without a key type segment fails the start check. See
 * `CHAT-avduuqwp`, T7.
 */
@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class)
@Tag("integration")
class RedisStoreShapeCheckTests(@Autowired private val stringTemplate: ReactiveStringRedisTemplate) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }

    @AfterEach
    fun `remove the legacy hash`() {
        stringTemplate.delete(RedisStoreShapeCheck.LEGACY_REGISTRY).block()
    }

    @Test
    fun `a store with only typed registries passes`() {
        stringTemplate.opsForHash<String, String>().put("chat:keys:uuid", "a", "b").block()

        assertThatCode { RedisStoreShapeCheck(stringTemplate).check() }.doesNotThrowAnyException()
    }

    @Test
    fun `a registry hash without a key type fails and names the recreation`() {
        stringTemplate.opsForHash<String, String>().put(RedisStoreShapeCheck.LEGACY_REGISTRY, "a", "b").block()

        assertThatThrownBy { RedisStoreShapeCheck(stringTemplate).check() }
            .hasMessageContaining("chat:keys")
            .hasMessageContaining("Recreate the store")
    }
}
