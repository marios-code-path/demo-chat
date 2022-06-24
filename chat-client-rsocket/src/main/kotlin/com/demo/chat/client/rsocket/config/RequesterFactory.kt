package com.demo.chat.client.rsocket.config

import org.springframework.messaging.rsocket.RSocketRequester

interface RequesterFactory {
    fun requester(serviceKey: String): RSocketRequester
    fun serviceDestination(serviceKey: String): String
}