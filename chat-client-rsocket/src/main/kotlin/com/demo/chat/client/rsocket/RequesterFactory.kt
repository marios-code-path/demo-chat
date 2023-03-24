package com.demo.chat.client.rsocket

import com.demo.chat.service.client.ClientFactory
import org.springframework.messaging.rsocket.RSocketRequester

interface RequesterFactory: ClientFactory<RSocketRequester> {
    override fun getClient(serviceKey: String): RSocketRequester
    override fun serviceDestination(serviceKey: String): String
}