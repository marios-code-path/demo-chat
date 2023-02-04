package com.demo.chat.test.persistence

import com.demo.chat.domain.*
import com.demo.chat.persistence.memory.impl.MembershipPersistenceInMemory
import com.demo.chat.persistence.memory.impl.MessagePersistenceInMemory
import com.demo.chat.persistence.memory.impl.TopicPersistenceInMemory
import com.demo.chat.persistence.memory.impl.UserPersistenceInMemory
import com.demo.chat.test.*


class PersistenceUserTests : PersistenceTestBase<String, User<String>>
    (TestUserSupplier, UserPersistenceInMemory(TestStringKeyService()) { t -> t.key })

class PersistenceMessageTests : PersistenceTestBase<String, Message<String, String>>
    (TestMessageSupplier, MessagePersistenceInMemory(TestStringKeyService()) { t -> t.key })

class PersistenceTopicTests : PersistenceTestBase<String, MessageTopic<String>>
    (TestMessageTopicSupplier, TopicPersistenceInMemory(TestStringKeyService()) { t -> t.key })

class PersistenceMembershipTests : PersistenceTestBase<String, TopicMembership<String>>
    (TestTopicMembershipSupplier, MembershipPersistenceInMemory(TestStringKeyService()) { t -> Key.funKey(t.key) })

//@ExtendWith(MockPersistenceResolver::class)
//class MockPersistenceTests(persistence: PersistenceStore<Number, Any>):
//        PersistenceTestBase<Number, Any>(TestAnySupplier, persistence)