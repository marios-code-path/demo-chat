package com.demo.chat.client.rsocket.transport

import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.netty.tcp.TcpClient

open class InsecureConnection : RSocketClientTransportFactory {

    override fun getClientTransport(host: String, port: Int): TcpClientTransport =
        TcpClientTransport.create(
            TcpClient.create()
                .host(host)
                .port(port)
                .secure { s ->
                    s.sslContext(
                        SslContextBuilder
                            .forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build()
                    )
                })
}