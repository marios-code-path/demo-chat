package com.demo.chat.test.controller.app

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import java.util.*

class TestModules : JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)

@TestConfiguration
@Import(TestModules::class,
        JacksonAutoConfiguration::class,
        RSocketStrategiesAutoConfiguration::class)
class TestConfigurationRSocket {
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
    private lateinit var topicService: PubSubTopicExchangeService<UUID, String>

    @MockBean
    private lateinit var keyService: IKeyService<UUID>

    @MockBean
    private lateinit var membershipPersistence: MembershipPersistence<UUID>

    @MockBean
    private lateinit var membershipIndex: MembershipIndexService<UUID, Map<String, String>>

    @Autowired
    private lateinit var rsocketStrategies: RSocketStrategies

    @Bean
    fun serverMessageHandler(strategies: RSocketStrategies): RSocketMessageHandler {
        val handler = RSocketMessageHandler()
        handler.rSocketStrategies = strategies
        handler.afterPropertiesSet()
        return handler
    }
}