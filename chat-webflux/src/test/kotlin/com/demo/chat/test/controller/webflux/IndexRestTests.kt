package com.demo.chat.test.controller.webflux

import com.demo.chat.test.key.TestKeys

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.controller.webflux.*
import com.demo.chat.domain.*
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.test.config.TestLongIndexBeans
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(classes = [TestLongIndexBeans::class, LongTypeUtilConfiguration::class, UserIndexRestController::class, WebFluxTestConfiguration::class])
class UserIndexRestTests(
    @Autowired beans: IndexServiceBeans<Long, String, IndexSearchRequest>
) : IndexRestTestBase<Long, User<Long>, IndexSearchRequest>(
    "user",
    { User.create(TestKeys.key(1001L), "userName", "userHandle", "imageUri") },
    { TestKeys.key(1001L) },
    { IndexSearchRequest("name", "userName", 100) },
    beans.userIndex()
)

@ContextConfiguration(classes = [TestLongIndexBeans::class, LongTypeUtilConfiguration::class, TopicIndexRestController::class, WebFluxTestConfiguration::class])
class TopicIndexRestTests(
    @Autowired beans: IndexServiceBeans<Long, String, IndexSearchRequest>
) : IndexRestTestBase<Long, MessageTopic<Long>, IndexSearchRequest>(
    "topic",
    { MessageTopic.create(TestKeys.key(1001L), "testTopic") },
    { TestKeys.key(1001L) },
    { IndexSearchRequest("name", "testTopic", 100) },
    beans.topicIndex()
)

@ContextConfiguration(classes = [TestLongIndexBeans::class, LongTypeUtilConfiguration::class, MembershipIndexRestController::class, WebFluxTestConfiguration::class])
class TopicMembershipIndexRestTests(
    @Autowired beans: IndexServiceBeans<Long, String, IndexSearchRequest>
) : IndexRestTestBase<Long, TopicMembership<Long>, IndexSearchRequest>(
    "membership",
    { TopicMembership.create(1001L, 1L, 201L) },
    { TestKeys.key(1001L) },
    { IndexSearchRequest("member", "120", 100) },
    beans.membershipIndex()
)

@ContextConfiguration(classes = [TestLongIndexBeans::class, LongTypeUtilConfiguration::class, MessageIndexRestController::class, WebFluxTestConfiguration::class])
class TopicMessageIndexRestTests(
    @Autowired beans: IndexServiceBeans<Long, String, IndexSearchRequest>
) : IndexRestTestBase<Long, Message<Long, String>, IndexSearchRequest>(
    "message",
    { Message.create(TestKeys.message(1001L, 1L, 201L), "Test", true) },
    { TestKeys.key(1001L) },
    { IndexSearchRequest(MessageIndexService.USER, "1", 100) },
    beans.messageIndex()
)

@ContextConfiguration(classes = [TestLongIndexBeans::class, LongTypeUtilConfiguration::class, AuthMetadataIndexRestController::class, WebFluxTestConfiguration::class])
class AuthMetadataIndexRestTests(
    @Autowired beans: IndexServiceBeans<Long, String, IndexSearchRequest>
) : IndexRestTestBase<Long, AuthMetadata<Long>, IndexSearchRequest>(
    "auth",
    { AuthMetadata.create(TestKeys.key(1001L), TestKeys.key(1L), TestKeys.key(201L), "TEST", false, Long.MAX_VALUE) },
    { TestKeys.key(1001L) },
    { IndexSearchRequest("member", "120", 100) },
    beans.authMetadataIndex()
)

@ContextConfiguration(classes = [TestLongIndexBeans::class, LongTypeUtilConfiguration::class, KeyValueIndexRestController::class, WebFluxTestConfiguration::class])
class KeyValueIndexRestTests(
    @Autowired beans: IndexServiceBeans<Long, String, IndexSearchRequest>
) : IndexRestTestBase<Long, KeyValuePair<Long, Any>, IndexSearchRequest>(
    "kv",
    { KeyValuePair.create(TestKeys.key(1001L), "TEST")},
    { TestKeys.key(1001L) },
    { IndexSearchRequest("data", "test", 100) },
    beans.KVPairIndex()
)