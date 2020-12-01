package com.demo.chat.test.persistence

import com.demo.chat.domain.*
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import com.demo.chat.test.*


class PersistenceUserTests : PersistenceTestBase<String, User<String>>
(TestUserSupplier, UserPersistenceInMemory(TestKeyService(), User::class.java) { t -> t.key })

class PersistenceMessageTests : PersistenceTestBase<String, Message<String, String>>
(TestMessageSupplier, MessagePersistenceInMemory(TestKeyService(), Message::class.java) { t -> t.key })

class PersistenceTopicTests : PersistenceTestBase<String, MessageTopic<String>>
(TestMessageTopicSupplier, TopicPersistenceInMemory(TestKeyService(), MessageTopic::class.java) { t -> t.key })

class PersistenceMembershipTests : PersistenceTestBase<String, TopicMembership<String>>
(TestTopicMembershipSupplier, MembershipPersistenceInMemory(TestKeyService(), TopicMembership::class.java) { t -> Key.funKey(t.key) })