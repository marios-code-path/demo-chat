package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.Message
import com.demo.chat.persistence.redis.impl.MessagePersistenceRedis
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
class RedisMessagePersistenceTests(
    @Autowired messagePersistence: MessagePersistenceRedis<UUID, String>,
    @Autowired stringTemplate: ReactiveStringRedisTemplate,
) : RedisPersistenceTestBase<UUID, Message<UUID, String>>(
    stringTemplate,
    TestUUIDMessageSupplier,
    messagePersistence,
    { t -> t.key },
    { original, roundTripped ->
        assertThat(roundTripped.data).isEqualTo(original.data)
        assertThat(roundTripped.record).isEqualTo(original.record)
        assertThat(roundTripped.key.id).isEqualTo(original.key.id)
        assertThat(roundTripped.key.from).isEqualTo(original.key.from)
        assertThat(roundTripped.key.dest).isEqualTo(original.key.dest)
    },
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}