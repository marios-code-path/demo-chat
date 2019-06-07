package com.demo.chat.chatconsumers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Component

@Component
class ChatUserClient(val socket: RSocketRequester) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    fun callCreateUser(name: String, handle: String) = socket
            .route("user-create")
            .data(UserCreateRequest(name, handle))
            .retrieveMono(ChatUser::class.java)

    fun callGetUser(handle: String) = socket
            .route("user-handle")
            .data(UserRequest(handle))
            .retrieveFlux(ChatUser::class.java)
}

