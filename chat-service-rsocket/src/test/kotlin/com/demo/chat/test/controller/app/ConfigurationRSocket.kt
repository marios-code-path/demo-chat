package com.demo.chat.test.controller.app

import com.demo.chat.service.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler

@TestConfiguration
@Import(JacksonAutoConfiguration::class, RSocketStrategiesAutoConfiguration::class)
class ConfigurationRSocket {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @MockBean
    private lateinit var roomIndex: RoomIndexService

    @MockBean
    private lateinit var userIndex: UserIndexService

    @MockBean
    private lateinit var messageIndex: MessageIndexService

    @MockBean
    private lateinit var roomPersistence: RoomPersistence

    @MockBean
    private lateinit var userPersistence: UserPersistence

    @MockBean
    private lateinit var topicMessagePersistence: TextMessagePersistence

    @MockBean
    private lateinit var topicService: ChatTopicService

    @MockBean
    private lateinit var keyService: KeyService

    @MockBean
    private lateinit var membershipPersistence: MembershipPersistence

    @MockBean
    private lateinit var membershipIndex: MembershipIndexService

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