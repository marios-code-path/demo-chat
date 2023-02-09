package com.demo.chat.secure.transport

import io.rsocket.transport.netty.client.TcpClientTransport

interface TransportFactory {
    fun tcpClientTransport(host: String, port: Int): TcpClientTransport
}