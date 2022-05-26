package com.demo.chat.streams.gateway


import org.springframework.integration.annotation.GatewayHeader
import org.springframework.integration.annotation.MessagingGateway


@MessagingGateway(
    name = "membershipEnricherGateway",
    defaultHeaders = [GatewayHeader(
        name = "calledMethod",
        expression = "#gatewayMethod.name"
    )]
)

interface MembershipEnricherGateway<T> {

}