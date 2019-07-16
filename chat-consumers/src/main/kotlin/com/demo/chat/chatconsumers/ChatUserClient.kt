package com.demo.chat.chatconsumers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Component
import java.util.*

@Component
class ChatUserClient(val socket: RSocketRequester) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    fun callCreateUser(name: String, handle: String, imageUri: String) = socket
            .route("user-add")
            .data(UserCreateRequest(name, handle, imageUri))
            .retrieveMono(Void::class.java)

    fun callGetUserByHandle(handle: String) = socket
            .route("user-by-handle")
            .data(UserRequest(handle))
            .retrieveFlux(ChatUser::class.java)

    fun callGetUserById(id: UUID) = socket
            .route("user-by-id")
            .data(UserRequestId(id))
            .retrieveFlux(ChatUser::class.java)
}

