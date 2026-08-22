package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.MessageTopic
import com.demo.chat.persistence.redis.impl.TopicPersistenceRedis
import org.assertj.core.api.Assertions.assertThat
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
class RedisTopicPersistenceTests(
    @Autowired topicPersistence: TopicPersistenceRedis<UUID>,
    @Autowired stringTemplate: ReactiveStringRedisTemplate,
) : RedisPersistenceTestBase<UUID, MessageTopic<UUID>>(
    stringTemplate,
    TestUUIDMessageTopicSupplier,
    topicPersistence,
    { t -> t.key },
    { original, roundTripped ->
        assertThat(roundTripped.data).isEqualTo(original.data)
        assertThat(roundTripped.key.id).isEqualTo(original.key.id)
    },
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}