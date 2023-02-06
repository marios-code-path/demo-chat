package com.demo.chat.client.rsocket

import org.springframework.messaging.rsocket.RSocketRequester

interface RequesterFactory {
    fun requester(serviceKey: String): RSocketRequester
    fun serviceDestination(serviceKey: String): String
}