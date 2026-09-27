package com.demo.chat.test.controller.webflux

import com.demo.chat.test.key.TestKeys

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.webflux.*
import com.demo.chat.controller.webflux.core.mapping.KVRequest
import com.demo.chat.domain.*
import com.demo.chat.test.config.TestLongPersistenceBeans
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(classes = [TestLongPersistenceBeans::class, LongTypeUtilConfiguration::class, UserPersistenceRestController::class, WebFluxTestConfiguration::class])
class UserPersistenceRestTests(
    @Autowired beans: PersistenceServiceBeans<Long, String>
) : PersistenceRestTestBase<Long, User<Long>>(
    "user",
    { User.create(TestKeys.key(1001L), "userName", "userHandle", "imageUri") },
    { TestKeys.key(1001L) },
    { UserCreateRequest("userName", "userHandle", "imageUri") },
    beans.userPersistence()
)

@ContextConfiguration(classes = [TestLongPersistenceBeans::class, LongTypeUtilConfiguration::class, TopicPersistenceRestController::class, WebFluxTestConfiguration::class])
class TopicPersistenceRestTests(
    @Autowired beans: PersistenceServiceBeans<Long, String>
) : PersistenceRestTestBase<Long, MessageTopic<Long>>(
    "topic",
    { MessageTopic.create(TestKeys.key(1001L), "TestTopic") },
    { TestKeys.key(1001L) },
    { ByStringRequest("TestTopic") },
    beans.topicPersistence()
)

@ContextConfiguration(classes = [TestLongPersistenceBeans::class, LongTypeUtilConfiguration::class, MessagePersistenceRestController::class, WebFluxTestConfiguration::class])
class MessagePersistenceRestTests(
    @Autowired beans: PersistenceServiceBeans<Long, String>
) : PersistenceRestTestBase<Long, Message<Long, String>>(
    "message",
    { Message.create(TestKeys.message(1001L, 1002L, 1003L), "TestMessage", true) },
    { TestKeys.key(1001L) },
    { MessageSendRequest("TestMessage", 1002, 1003) },
    beans.messagePersistence()
)

@ContextConfiguration(classes = [TestLongPersistenceBeans::class, LongTypeUtilConfiguration::class, MembershipPersistenceRestController::class, WebFluxTestConfiguration::class])
class MembershipPersistenceRestTests(
    @Autowired beans: PersistenceServiceBeans<Long, String>
) : PersistenceRestTestBase<Long, TopicMembership<Long>>(
    "membership",
    { TopicMembership.create(1001L, 1002L, 1003L) },
    { TestKeys.key(1001L) },
    { MembershipRequest(1002, 1003) },
    beans.membershipPersistence()
)

@ContextConfiguration(classes = [TestLongPersistenceBeans::class, LongTypeUtilConfiguration::class, KeyValueStoreRestController::class, WebFluxTestConfiguration::class])
class KeyValuePersistenceRestTests(
    @Autowired beans: PersistenceServiceBeans<Long, String>
) : PersistenceRestTestBase<Long, KeyValuePair<Long, Any>>(
    "kv",
    { KeyValuePair.create(TestKeys.key(1001L), "TestData") },
    { TestKeys.key(1001L) },
    { KVRequest(1001L, "TestData") },
    beans.keyValuePersistence()
)
