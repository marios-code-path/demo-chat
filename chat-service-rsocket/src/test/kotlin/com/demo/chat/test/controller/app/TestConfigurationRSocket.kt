package com.demo.chat.test.controller.app

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.Message
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.*
import org.slf4j.LoggerFactory
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
    val log = LoggerFactory.getLogger(this::class.simpleName)


    @MockBean
    private lateinit var topicIndex: TopicIndexService<UUID>
    @MockBean
    private lateinit var topicPersistence: TopicPersistence<UUID>


    @MockBean
    private lateinit var userPersistence: UserPersistence<UUID>
    @MockBean
    private lateinit var userIndex: UserIndexService<UUID>

    @MockBean
    private lateinit var topicMessagePersistence: MessagePersistence<UUID, String>
    @MockBean
    private lateinit var messageIndex:  MessageIndexService<UUID>

    @MockBean
    private lateinit var topicService: ChatTopicMessagingService<UUID, String>

    @MockBean
    private lateinit var keyService: IKeyService<UUID>

    @MockBean
    private lateinit var membershipPersistence: MembershipPersistence<UUID>
    @MockBean
    private lateinit var membershipIndex: MembershipIndexService<UUID>

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