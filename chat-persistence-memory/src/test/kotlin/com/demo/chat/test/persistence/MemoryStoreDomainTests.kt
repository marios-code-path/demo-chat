package com.demo.chat.test.persistence

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.memory.impl.AuthMetaPersistenceInMemory
import com.demo.chat.persistence.memory.impl.InMemoryKeyValueStore
import com.demo.chat.persistence.memory.impl.MembershipPersistenceInMemory
import com.demo.chat.persistence.memory.impl.MessagePersistenceInMemory
import com.demo.chat.persistence.memory.impl.TopicPersistenceInMemory
import com.demo.chat.persistence.memory.impl.UserPersistenceInMemory
import com.demo.chat.test.key.FakeKeyServices

/** The memory stores refuse a key of another domain. See `CHAT-avduuqwp`, T5. */
private val ROOTS = FakeKeyServices.longRoots()
private val KEYS = FakeKeyServices.long(ROOTS)

class MemoryUserStoreDomainTests : StoreDomainTestBase<Long, User<Long>>(
    UserPersistenceInMemory(KEYS, ROOTS) { it.key }, KEYS, ROOTS, ChatDomain.USER,
    { User.create(it, "n", "h-${it.id}", "http://u") }, { it.key },
)

class MemoryMessageStoreDomainTests : StoreDomainTestBase<Long, Message<Long, String>>(
    MessagePersistenceInMemory(KEYS, ROOTS) { it.key }, KEYS, ROOTS, ChatDomain.MESSAGE,
    { Message.create(MessageKey.of(it.id, it.root, 1L, 2L), "m", true) }, { Key.of(it.key.id, it.key.root) },
)

class MemoryTopicStoreDomainTests : StoreDomainTestBase<Long, MessageTopic<Long>>(
    TopicPersistenceInMemory(KEYS, ROOTS) { it.key }, KEYS, ROOTS, ChatDomain.MESSAGE_TOPIC,
    { MessageTopic.create(it, "room-${it.id}") }, { it.key },
)

class MemoryMembershipStoreDomainTests : StoreDomainTestBase<Long, TopicMembership<Long>>(
    MembershipPersistenceInMemory(KEYS, ROOTS) { Key.of(it.key, ROOTS.of(ChatDomain.TOPIC_MEMBERSHIP).id) },
    KEYS, ROOTS, ChatDomain.TOPIC_MEMBERSHIP,
    { TopicMembership.create(it.id, 1L, 2L) }, { Key.of(it.key, ROOTS.of(ChatDomain.TOPIC_MEMBERSHIP).id) },
)

class MemoryAuthMetaStoreDomainTests : StoreDomainTestBase<Long, AuthMetadata<Long>>(
    AuthMetaPersistenceInMemory(KEYS, ROOTS) { it.key }, KEYS, ROOTS, ChatDomain.AUTH_METADATA,
    { AuthMetadata.create(it, ROOTS.anon(), ROOTS.of(ChatDomain.USER), "GET", false, Long.MAX_VALUE) }, { it.key },
)

class MemoryKeyValueStoreDomainTests : StoreDomainTestBase<Long, KeyValuePair<Long, Any>>(
    InMemoryKeyValueStore(KEYS, ROOTS) { it.key }, KEYS, ROOTS, ChatDomain.KEY_VALUE_PAIR,
    { KeyValuePair.create(it, "v" as Any) }, { it.key },
)
