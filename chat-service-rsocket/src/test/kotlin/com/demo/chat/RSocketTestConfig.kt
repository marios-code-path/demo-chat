package com.demo.chat

import com.demo.chat.service.*
import io.rsocket.SocketAcceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler

@Configuration
@Import(JacksonAutoConfiguration::class, RSocketStrategiesAutoConfiguration::class)
class RSocketTestConfig {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @MockBean
    private lateinit var roomIndex: ChatRoomIndexService

    @MockBean
    private lateinit var userIndex: ChatUserIndexService

    @MockBean
    private lateinit var messageIndex: ChatMessageIndexService

    @MockBean
    private lateinit var roomPersistence: ChatRoomPersistence

    @MockBean
    private lateinit var userPersistence: ChatUserPersistence

    @MockBean
    private lateinit var topicMessagePersistence: TextMessagePersistence

    @MockBean
    private lateinit var topicService: ChatTopicService

    @MockBean
    private lateinit var keyService: KeyService

    @MockBean
    private lateinit var membershipPersistence: ChatMembershipPersistence

    @MockBean
    private lateinit var membershipIndex: ChatMembershipIndexService

    @Autowired
    private lateinit var rsocketStrategies: RSocketStrategies

    @Bean
    fun responderAcceptor(handler: RSocketMessageHandler): SocketAcceptor = handler.responder()

    @Bean
    fun serverMessageHandler(strategies: RSocketStrategies): RSocketMessageHandler {
        val handler = RSocketMessageHandler()
        handler.rSocketStrategies = strategies
        handler.afterPropertiesSet()
        return handler
    }
}