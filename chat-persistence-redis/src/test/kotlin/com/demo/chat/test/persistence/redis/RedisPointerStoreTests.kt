package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.redis.impl.KeyServiceRedis
import com.demo.chat.persistence.redis.impl.MessagePersistenceRedis
import com.demo.chat.test.persistence.PointerStoreTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID

/** The Redis store writes a pointer as JSON and reads it back through Jackson. See `CHAT-erkoxybm`. */
@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisPointerStoreTests @Autowired constructor(
    store: MessagePersistenceRedis<UUID, String>,
    keys: KeyServiceRedis<UUID>,
    rootKeys: RootKeys<UUID>,
) : PointerStoreTestBase<UUID>(store, keys, rootKeys) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}
