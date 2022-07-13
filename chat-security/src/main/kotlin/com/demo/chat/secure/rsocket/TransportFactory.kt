package com.demo.chat.secure.rsocket

import io.rsocket.transport.netty.client.TcpClientTransport

interface TransportFactory {
    fun tcpClientTransport(host: String, port: Int): TcpClientTransport
}