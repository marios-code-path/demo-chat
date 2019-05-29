package com.demo.chat.chatconsumers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Component
import java.util.*

@Component
class ChatUserClient(val socket: RSocketRequester) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    fun callCreateUser(name: String, handle: String) = socket
            .route("user-create")
            .data(UserCreateRequest(name, handle))
            .retrieveMono(UserCreateResponse::class.java)


    fun callGetUser(handle: String) = socket
            .route("user-handle")
            .data(UserRequest(handle))
            .retrieveFlux(UserResponse::class.java)
            .doOnNext {
                logger.info("The user was :${it.user}")
            }
}

