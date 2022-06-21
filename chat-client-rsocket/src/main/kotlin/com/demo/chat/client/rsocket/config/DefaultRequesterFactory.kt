package com.demo.chat.client.rsocket.config

import io.rsocket.transport.netty.client.TcpClientTransport
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies

class DefaultRequesterFactory(
    private val builder: RSocketRequester.Builder,
    private val strategies: RSocketStrategies,
    private val host: String = "127.0.0.1",
    private val port: Int = 6790,
    transport: TcpClientTransport
) : RequesterFactory {
    override fun requester(serviceKey: String): RSocketRequester = builder
        .rsocketStrategies(strategies)
        .connectTcp(host, port)
        .log()
        .block()!!
}