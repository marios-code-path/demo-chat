package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.User
import com.demo.chat.persistence.redis.impl.UserPersistenceRedis
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
class RedisUserPersistenceTests(
    @Autowired userPersistence: UserPersistenceRedis<UUID>,
    @Autowired stringTemplate: ReactiveStringRedisTemplate,
) : RedisKeyAwarePersistenceTestBase<UUID, User<UUID>>(
    stringTemplate,
    TestUUIDUserSupplier,
    userPersistence,
    { t -> t.key },
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}