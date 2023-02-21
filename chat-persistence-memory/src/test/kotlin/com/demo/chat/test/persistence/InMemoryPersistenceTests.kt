package com.demo.chat.test.persistence

import com.demo.chat.domain.*
import com.demo.chat.persistence.memory.impl.*
import com.demo.chat.test.*
import org.junit.jupiter.api.TestInstance


class PersistenceUserTests : KeyAwarePersistenceTestBase<String, User<String>>
    (TestUserSupplier, UserPersistenceInMemory(TestStringKeyService()) { t -> t.key }, {t -> t.key })

class PersistenceMessageTests : KeyAwarePersistenceTestBase<String, Message<String, String>>
    (TestMessageSupplier, MessagePersistenceInMemory(TestStringKeyService()) { t -> t.key }, {t -> t.key })

class PersistenceTopicTests : KeyAwarePersistenceTestBase<String, MessageTopic<String>>
    (TestMessageTopicSupplier, TopicPersistenceInMemory(TestStringKeyService()) { t -> t.key }, {t -> t.key })

class PersistenceMembershipTests : KeyAwarePersistenceTestBase<String, TopicMembership<String>>
    (TestTopicMembershipSupplier, MembershipPersistenceInMemory(TestStringKeyService()) { t -> Key.funKey(t.key) }, {t -> Key.funKey(t.key) })

class PersistenceAuthmetadataTests : KeyAwarePersistenceTestBase<String, AuthMetadata<String>>
    (TestAuthMetaSupplier, AuthMetaPersistenceInMemory(TestStringKeyService()) { t -> t.key }, {t -> t.key })

//@ExtendWith(MockPersistenceResolver::class)
//class MockPersistenceTests(persistence: PersistenceStore<Number, Any>):
//        PersistenceTestBase<Number, Any>(TestAnySupplier, persistence)