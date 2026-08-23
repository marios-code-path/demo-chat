package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.persistence.redis.impl.AuthMetaPersistenceRedis
import org.assertj.core.api.Assertions.assertThat
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
class RedisAuthMetaPersistenceTests(
    @Autowired authMetaPersistence: AuthMetaPersistenceRedis<UUID>,
    @Autowired stringTemplate: ReactiveStringRedisTemplate,
) : RedisPersistenceTestBase<UUID, AuthMetadata<UUID>>(
    stringTemplate,
    TestUUIDAuthMetaSupplier,
    authMetaPersistence,
    { t -> t.key },
    { original, roundTripped ->
        assertThat(roundTripped.principal.id).isEqualTo(original.principal.id)
        assertThat(roundTripped.target.id).isEqualTo(original.target.id)
        assertThat(roundTripped.permission).isEqualTo(original.permission)
        assertThat(roundTripped.mute).isEqualTo(original.mute)
        assertThat(roundTripped.expires).isEqualTo(original.expires)
    },
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}