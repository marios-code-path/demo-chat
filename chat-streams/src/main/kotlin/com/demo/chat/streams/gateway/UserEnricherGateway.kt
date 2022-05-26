package com.demo.chat.streams.gateway

import com.demo.chat.domain.User
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import com.demo.chat.streams.functions.UserCreateRequest
import org.springframework.integration.annotation.Gateway
import org.springframework.integration.annotation.GatewayHeader
import org.springframework.integration.annotation.MessagingGateway

@MessagingGateway(
    name = "userEnricherGateway",
    defaultHeaders = [GatewayHeader(
        name = "calledMethod",
        expression = "#gatewayMethod.name"
    )]
)
interface UserEnricherGateway<T> {
    @Gateway(requestChannel = "users.add.req", replyChannel = "users.add.res")
    fun add(ent: UserCreateRequest): User<T>

    @Gateway(requestChannel = "users.rem.req", replyChannel = "users.rem.res")
    fun rem(ent: User<T>): Void

    @Gateway(requestChannel = "users.update.req", replyChannel = "users.update.res")
    fun update(ent: User<T>): User<T>
}

