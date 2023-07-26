package com.demo.chat.test.rsocket.controller.composite

import com.demo.chat.service.core.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.messaging.rsocket.RSocketStrategies
import java.util.*

@TestConfiguration
class MockCoreServicesConfiguration {
    @MockBean
    private lateinit var topicIndex: TopicIndexService<UUID, Map<String, String>>

    @MockBean
    private lateinit var topicPersistence: TopicPersistence<UUID>

    @MockBean
    private lateinit var userPersistence: UserPersistence<UUID>

    @MockBean
    private lateinit var userIndex: UserIndexService<UUID, Map<String, String>>

    @MockBean
    private lateinit var topicMessagePersistence: MessagePersistence<UUID, String>

    @MockBean
    private lateinit var messageIndex: MessageIndexService<UUID, String, Map<String, String>>

    @MockBean
    private lateinit var topicServiceTopic: TopicPubSubService<UUID, String>

    @MockBean
    private lateinit var keyService: IKeyService<UUID>

    @MockBean
    private lateinit var membershipPersistence: MembershipPersistence<UUID>

    @MockBean
    private lateinit var membershipIndex: MembershipIndexService<UUID, Map<String, String>>

    @Autowired
    private lateinit var rsocketStrategies: RSocketStrategies
}