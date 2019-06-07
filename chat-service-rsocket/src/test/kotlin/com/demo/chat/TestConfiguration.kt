package com.demo.chat

import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TextMessage
import com.demo.chat.service.ChatMessageService
import com.demo.chat.service.ChatRoomServiceCassandra
import com.demo.chat.service.ChatUserServiceCassandra
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.server.RSocketServerBootstrap
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Configuration


@Configuration
class TestSetupConfig {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @MockBean
    private lateinit var roomService: ChatRoomServiceCassandra

    @MockBean
    private lateinit var userService: ChatUserServiceCassandra

    @MockBean
    private lateinit var messageService: ChatMessageService<TextMessage, MessageKey>

    @Autowired
    private lateinit var rsboot: RSocketServerBootstrap

    fun rsocketInit() = when (rsboot.isRunning) {
            false -> {
                log.warn("RSocket Service is not already running")
                rsboot.start()
            }
            else -> log.warn("RSocket Service is already running")
        }
}

