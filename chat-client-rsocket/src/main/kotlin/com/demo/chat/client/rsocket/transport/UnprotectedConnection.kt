package com.demo.chat.client.rsocket.transport

import io.rsocket.transport.ClientTransport
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.client.WebsocketClientTransport
import reactor.netty.tcp.TcpClient

class UnprotectedConnection(override val webSocket: Boolean = false) : RSocketClientTransportFactory {
    override fun getClientTransport(host: String, port: Int): ClientTransport =
        if (webSocket) {
            WebsocketClientTransport
                .create(TcpClient.create().host(host).port(port))
        } else {
            TcpClientTransport.create(TcpClient.create().host(host).port(port))
        }
}