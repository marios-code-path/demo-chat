package com.demo.chat.test.persistence

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.TestRoots
import com.demo.chat.service.core.IKeyService
import reactor.core.publisher.Mono

import com.demo.chat.test.key.RootKeysFixture

import com.demo.chat.domain.*
import com.demo.chat.persistence.memory.impl.*
import com.demo.chat.test.*
import com.demo.chat.test.key.TestKeys
import org.junit.jupiter.api.TestInstance
import org.springframework.core.ParameterizedTypeReference
import java.util.function.Supplier


class PersistenceUserTests : KeyAwarePersistenceTestBase<String, User<String>>
    (TestUserSupplier, UserPersistenceInMemory(TestStringKeyService(), RootKeysFixture.forTestRoot(ChatDomain.USER, String::class.java)) { t -> t.key }, {t -> t.key })

class PersistenceMessageTests : KeyAwarePersistenceTestBase<String, Message<String, String>>
    (TestMessageSupplier, MessagePersistenceInMemory(TestStringKeyService(), RootKeysFixture.forTestRoot(ChatDomain.MESSAGE, String::class.java)) { t -> t.key }, {t -> t.key })

class PersistenceTopicTests : KeyAwarePersistenceTestBase<String, MessageTopic<String>>
    (TestMessageTopicSupplier, TopicPersistenceInMemory(TestStringKeyService(), RootKeysFixture.forTestRoot(ChatDomain.MESSAGE_TOPIC, String::class.java)) { t -> t.key }, {t -> t.key })

class PersistenceMembershipTests : KeyAwarePersistenceTestBase<String, TopicMembership<String>>
    (TestTopicMembershipSupplier, MembershipPersistenceInMemory(membershipKeys, RootKeysFixture.forTestRoot(ChatDomain.TOPIC_MEMBERSHIP, String::class.java)) { t -> TestKeys.key(t.key) }, {t -> TestKeys.key(t.key) })

class PersistenceAuthmetadataTests : KeyAwarePersistenceTestBase<String, AuthMetadata<String>>
    (TestAuthMetaSupplier, AuthMetaPersistenceInMemory(TestStringKeyService(), RootKeysFixture.forTestRoot(ChatDomain.AUTH_METADATA, String::class.java)) { t -> t.key }, {t -> t.key })

class PersistenceKeyValueStoreTests : KeyAwarePersistenceTestBase<String, KeyValuePair<String, Any>>
    (TestKeyValuePairSupplier, InMemoryKeyValueStore(TestStringKeyService(), RootKeysFixture.forTestRoot(ChatDomain.KEY_VALUE_PAIR, String::class.java)) { t -> t.key }, {t -> t.key })

class TypedKeyValueStoreTests : KeyValueStoreTestBase<String, Any>
    (TestKeyValuePairSupplier,
     Supplier { String::class.java },
    InMemoryKeyValueStore(TestStringKeyService(), RootKeysFixture.forTestRoot(ChatDomain.KEY_VALUE_PAIR, String::class.java)) { t -> t.key }, {t -> t.key })

//@ExtendWith(MockPersistenceResolver::class)
//class MockPersistenceTests(persistence: PersistenceStore<Number, Any>):
//        PersistenceTestBase<Number, Any>(TestAnySupplier, persistence)

/** It mints as before, and it answers the test root for every id, so a membership passes the domain check. */
private val membershipKeys: IKeyService<String> = object : IKeyService<String> by TestStringKeyService() {
    override fun rootOf(id: String): Mono<String> = Mono.just(TestRoots.STRING)
}
