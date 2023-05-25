package com.demo.chat.client.rsocket.transport

import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport

interface RSocketClientTransportFactory : ClientTransportFactory<ClientTransport> {
    val webSocket: Boolean
    override fun getClientTransport(host: String, port: Int): ClientTransport
}

