package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.persistence.redis.impl.MembershipPersistenceRedis
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
class RedisMembershipPersistenceTests(
    @Autowired membershipPersistence: MembershipPersistenceRedis<UUID>,
    @Autowired stringTemplate: ReactiveStringRedisTemplate,
) : RedisPersistenceTestBase<UUID, TopicMembership<UUID>>(
    stringTemplate,
    TestUUIDTopicMembershipSupplier,
    membershipPersistence,
    { t -> Key.funKey(t.key) },
    { original, roundTripped ->
        assertThat(roundTripped.key).isEqualTo(original.key)
        assertThat(roundTripped.member).isEqualTo(original.member)
        assertThat(roundTripped.memberOf).isEqualTo(original.memberOf)
    },
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}