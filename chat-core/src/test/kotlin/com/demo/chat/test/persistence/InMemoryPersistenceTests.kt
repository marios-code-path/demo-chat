package com.demo.chat.test.persistence

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import com.demo.chat.test.*


class PersistenceUserTests
    : PersistenceTestBase<String, User<String>>(TestUserSupplier, UserPersistenceInMemory(TestKeyService()))

class PersistenceMessageTests : PersistenceTestBase<String, Message<String, String>>
(TestMessageSupplier, MessagePersistenceInMemory(TestKeyService()))

class PersistenceTopicTests : PersistenceTestBase<String, MessageTopic<String>>
(TestMessageTopicSupplier, TopicPersistenceInMemory(TestKeyService()))

class PersistenceMembershipTests : PersistenceTestBase<String, TopicMembership<String>>
(TestTopicMembershipSupplier, MembershipPersistenceInMemory(TestKeyService()))