package com.demo.chat.client.rsocket.transport

import com.demo.chat.service.client.transport.ClientTransportFactory
import io.netty.handler.ssl.SslContext
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.netty.tcp.TcpClient

interface RSocketClientTransportFactory : ClientTransportFactory<ClientTransport> {
    override fun getClientTransport(host: String, port: Int): ClientTransport
}

interface SSLClientTransportFactory : RSocketClientTransportFactory {
    fun getSSLContext(): SslContext

    override fun getClientTransport(host: String, port: Int): TcpClientTransport {

        return TcpClientTransport.create(
            TcpClient.create()
                .host(host)
                .port(port)
                .secure { s ->
                    s.sslContext(getSSLContext())
                })
    }
}