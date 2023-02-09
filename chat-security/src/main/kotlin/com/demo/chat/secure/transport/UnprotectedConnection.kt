package com.demo.chat.secure.transport

import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.netty.tcp.TcpClient

class UnprotectedConnection : TransportFactory {
    override fun tcpClientTransport(host: String, port: Int): TcpClientTransport =
        TcpClientTransport.create(
            TcpClient.create()
                .host(host)
                .port(port))
}