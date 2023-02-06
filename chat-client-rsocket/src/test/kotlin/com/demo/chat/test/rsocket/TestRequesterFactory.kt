package com.demo.chat.test.rsocket

import com.demo.chat.client.rsocket.RequesterFactory
import org.springframework.messaging.rsocket.RSocketRequester


class TestRequesterFactory(private val req: RSocketRequester) : RequesterFactory {
    override fun requester(serviceKey: String): RSocketRequester = req

    override fun serviceDestination(serviceKey: String): String = ""
}