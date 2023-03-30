package com.demo.chat.client.rsocket.transport

import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.netty.tcp.TcpClient

class UnprotectedConnection : RSocketClientTransportFactory {
    override fun getClientTransport(host: String, port: Int): TcpClientTransport =
        TcpClientTransport.create(
            TcpClient.create()
                .host(host)
                .port(port))
}