package com.demo.chat.service.client.transport

interface ClientTransportFactory<T> {
    fun getClientTransport(host: String, port: Int): T
}