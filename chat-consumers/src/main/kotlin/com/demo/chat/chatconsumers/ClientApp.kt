package com.demo.chat.chatconsumers

import io.rsocket.transport.netty.client.TcpClientTransport
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Component

@Component
class ChatUserClient {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @Bean
    fun requester(builder: RSocketRequester.Builder): RSocketRequester? = builder.connect(TcpClientTransport.create(7070)).block()

    //@Bean
    fun callCreateUser(socket: RSocketRequester) = ApplicationRunner {
        logger.info("START")
        socket.route("user-create")
                .data(UserCreateRequest("Mario1", "luigi"))
                .retrieveMono(UserResponse::class.java)
                .block()

        logger.warn("STOPPED")
    }

    @Bean
    fun callGetUser(socket: RSocketRequester) = ApplicationRunner {
        logger.info("START")
        socket.route("user-handle")
                .data(UserRequest("luigi"))
                .retrieveFlux(UserResponse::class.java)
                .doOnNext {
                    logger.info("The user was :${it.user}")
                }
                .blockFirst()

        logger.warn("STOP")
    }

}
