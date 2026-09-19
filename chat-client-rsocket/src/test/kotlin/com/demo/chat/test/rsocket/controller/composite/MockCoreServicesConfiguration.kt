package com.demo.chat.test.rsocket.controller.composite

import com.demo.chat.service.core.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.messaging.rsocket.RSocketStrategies
import java.util.*

@TestConfiguration
class MockCoreServicesConfiguration {
    @MockitoBean
    private lateinit var topicIndex: TopicIndexService<UUID, Map<String, String>>

    @MockitoBean
    private lateinit var topicPersistence: TopicPersistence<UUID>

    @MockitoBean
    private lateinit var userPersistence: UserPersistence<UUID>

    @MockitoBean
    private lateinit var userIndex: UserIndexService<UUID, Map<String, String>>

    @MockitoBean
    private lateinit var topicMessagePersistence: MessagePersistence<UUID, String>

    @MockitoBean
    private lateinit var messageIndex: MessageIndexService<UUID, String, Map<String, String>>

    @MockitoBean
    private lateinit var topicServiceTopic: TopicPubSubService<UUID, String>

    @MockitoBean
    private lateinit var keyService: IKeyService<UUID>

    @MockitoBean
    private lateinit var membershipPersistence: MembershipPersistence<UUID>

    @MockitoBean
    private lateinit var membershipIndex: MembershipIndexService<UUID, Map<String, String>>

    @Autowired
    private lateinit var rsocketStrategies: RSocketStrategies
}