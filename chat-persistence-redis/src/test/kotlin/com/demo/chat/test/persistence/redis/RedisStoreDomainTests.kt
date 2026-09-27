package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.redis.impl.*
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.persistence.StoreDomainTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID

// The Redis stores refuse a key of another domain. See CHAT-avduuqwp, T5.

@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisUserStoreDomainTests(
    @Autowired store: UserPersistenceRedis<UUID>,
    @Autowired keys: KeyServiceRedis<UUID>,
    @Autowired rootKeys: RootKeys<UUID>,
) : StoreDomainTestBase<UUID, User<UUID>>(store, keys, rootKeys, ChatDomain.USER, { User.create(it, "n", "h-${it.id}", "http://u") }, { it.key }) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}

@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisTopicStoreDomainTests(
    @Autowired store: TopicPersistenceRedis<UUID>,
    @Autowired keys: KeyServiceRedis<UUID>,
    @Autowired rootKeys: RootKeys<UUID>,
) : StoreDomainTestBase<UUID, MessageTopic<UUID>>(store, keys, rootKeys, ChatDomain.MESSAGE_TOPIC, { MessageTopic.create(it, "room") }, { it.key }) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}

@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisMessageStoreDomainTests(
    @Autowired store: MessagePersistenceRedis<UUID, String>,
    @Autowired keys: KeyServiceRedis<UUID>,
    @Autowired rootKeys: RootKeys<UUID>,
) : StoreDomainTestBase<UUID, Message<UUID, String>>(store, keys, rootKeys, ChatDomain.MESSAGE, { Message.create(MessageKey.of(it.id, it.root, UUID.randomUUID(), UUID.randomUUID()), "m", true) }, { Key.of(it.key.id, it.key.root) }) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}

@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisMembershipStoreDomainTests(
    @Autowired store: MembershipPersistenceRedis<UUID>,
    @Autowired keys: KeyServiceRedis<UUID>,
    @Autowired rootKeys: RootKeys<UUID>,
) : StoreDomainTestBase<UUID, TopicMembership<UUID>>(store, keys, rootKeys, ChatDomain.TOPIC_MEMBERSHIP, { TopicMembership.create(it.id, UUID.randomUUID(), UUID.randomUUID()) }, { Key.of(it.key, FakeKeyServices.uuidRoots().of(ChatDomain.TOPIC_MEMBERSHIP).id) }) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}

@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisAuthMetaStoreDomainTests(
    @Autowired store: AuthMetaPersistenceRedis<UUID>,
    @Autowired keys: KeyServiceRedis<UUID>,
    @Autowired rootKeys: RootKeys<UUID>,
) : StoreDomainTestBase<UUID, AuthMetadata<UUID>>(store, keys, rootKeys, ChatDomain.AUTH_METADATA, { AuthMetadata.create(it, Key.of(UUID.randomUUID(), it.root), Key.of(UUID.randomUUID(), it.root), "GET", false, Long.MAX_VALUE) }, { it.key }) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}

@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisKeyValueStoreDomainTests(
    @Autowired store: KeyValuePersistenceRedis<UUID>,
    @Autowired keys: KeyServiceRedis<UUID>,
    @Autowired rootKeys: RootKeys<UUID>,
) : StoreDomainTestBase<UUID, KeyValuePair<UUID, Any>>(store, keys, rootKeys, ChatDomain.KEY_VALUE_PAIR, { KeyValuePair.create(it, "v" as Any) }, { it.key }) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}
