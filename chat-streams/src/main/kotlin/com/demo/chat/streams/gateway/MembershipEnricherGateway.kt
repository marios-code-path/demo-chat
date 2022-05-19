package com.demo.chat.streams.gateway

import com.demo.chat.domain.User
import com.demo.chat.streams.functions.UserCreateRequest
import org.springframework.integration.annotation.Gateway
import org.springframework.integration.annotation.GatewayHeader
import org.springframework.integration.annotation.MessagingGateway


@MessagingGateway(
    name = "membershipEnricherGateway",
    defaultHeaders = [GatewayHeader(
        name = "calledMethod",
        expression = "#gatewayMethod.name"
    )]
)  // TODO : EnricherPersistenceStore<T, UserCreateRequest, User<T>>
interface MembershipEnricherGateway<T> {

}