package com.demo.chat

import com.demo.chat.controllers.MessageController
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.fasterxml.jackson.core.io.JsonStringEncoder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.codec.*
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.messaging.rsocket.MessageHandlerAcceptor
import org.springframework.messaging.rsocket.RSocketMessageHandler
import org.springframework.messaging.rsocket.RSocketStrategies

@Configuration
@Import(JacksonAutoConfiguration::class, RSocketStrategiesAutoConfiguration::class)
class rsockettestconfig {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @MockBean
    private lateinit var roomPersistence: ChatRoomPersistence<out Room, RoomKey>

    @MockBean
    private lateinit var userPersistence: ChatUserPersistence<out User, UserKey>

    @MockBean
    private lateinit var topicMessagePersistence: TextMessagePersistence<out TextMessage, TextMessageKey>

    @MockBean
    private lateinit var topicService: ChatTopicService

    @MockBean
    private lateinit var keyService: KeyService

    @Autowired
    private lateinit var rsocketStrategies: RSocketStrategies

    @Bean
    fun handlerAcceptor(strategies: RSocketStrategies): MessageHandlerAcceptor {
        val acc = MessageHandlerAcceptor()
        acc.rSocketStrategies = strategies
        acc.afterPropertiesSet()
        return acc
    }

    @Bean
    fun messageHandler(strategies: RSocketStrategies ): RSocketMessageHandler {
        val handler = RSocketMessageHandler()
        handler.rSocketStrategies = strategies
        handler.afterPropertiesSet()
        return handler
    }


}