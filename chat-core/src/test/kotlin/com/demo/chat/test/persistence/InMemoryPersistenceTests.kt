package com.demo.chat.test.persistence

import com.demo.chat.domain.*
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import com.demo.chat.test.*
import org.junit.jupiter.api.extension.ExtendWith


class PersistenceUserTests : PersistenceTestBase<String, User<String>>
(TestUserSupplier, UserPersistenceInMemory(TestStringKeyService(), User::class.java) { t -> t.key })

class PersistenceMessageTests : PersistenceTestBase<String, Message<String, String>>
(TestMessageSupplier, MessagePersistenceInMemory(TestStringKeyService(), Message::class.java) { t -> t.key })

class PersistenceTopicTests : PersistenceTestBase<String, MessageTopic<String>>
(TestMessageTopicSupplier, TopicPersistenceInMemory(TestStringKeyService(), MessageTopic::class.java) { t -> t.key })

class PersistenceMembershipTests : PersistenceTestBase<String, TopicMembership<String>>
(TestTopicMembershipSupplier, MembershipPersistenceInMemory(TestStringKeyService(), TopicMembership::class.java) { t -> Key.funKey(t.key) })

//@ExtendWith(MockPersistenceResolver::class)
//class MockPersistenceTests(persistence: PersistenceStore<Number, Any>):
//        PersistenceTestBase<Number, Any>(TestAnySupplier, persistence)