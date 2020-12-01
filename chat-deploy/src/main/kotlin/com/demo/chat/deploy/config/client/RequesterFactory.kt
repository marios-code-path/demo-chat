package com.demo.chat.deploy.config.client

import org.springframework.messaging.rsocket.RSocketRequester

interface RequesterFactory {
    fun requester(serviceKey: String): RSocketRequester
}