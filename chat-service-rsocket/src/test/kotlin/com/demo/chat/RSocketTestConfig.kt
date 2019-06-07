package com.demo.chat

import com.demo.chat.domain.*
import com.demo.chat.service.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.server.RSocketServerBootstrap
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Configuration


@Configuration
class RSocketTestConfig {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @MockBean
    private lateinit var roomService: ChatRoomService<out Room<RoomKey>, RoomKey>

    @MockBean
    private lateinit var userService: ChatUserService<out ChatUser, UserKey>

    @MockBean
    private lateinit var messageService: ChatMessageService<out TextMessage, MessageKey>

    @Autowired
    private lateinit var rsboot: RSocketServerBootstrap

    fun rSocketInit() = when (rsboot.isRunning) {
            false -> {
                log.warn("RSocket Service is not already running")
                rsboot.start()
            }
            else -> log.warn("RSocket Service is already running")
        }

    fun rSocketComplete() = when(rsboot.isRunning) {
        false -> {
            log.warn("rSocket acdtive on shutdown")
            rsboot.stop()
        }
        else -> {
            log.warn("rSocket Was already not Running.")

        }

    }
}

