package com.demo.chat.test.rsocket.controller.edge

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import java.util.*

class TestModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

@TestConfiguration
@Import(TestModules::class,
        JacksonAutoConfiguration::class,
        RSocketStrategiesAutoConfiguration::class,
        RSocketMessagingAutoConfiguration::class)
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