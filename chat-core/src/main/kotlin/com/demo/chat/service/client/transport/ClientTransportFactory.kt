package com.demo.chat.service.client.transport

interface ClientTransportFactory<T> {
    fun tcpClientTransport(host: String, port: Int): T
}