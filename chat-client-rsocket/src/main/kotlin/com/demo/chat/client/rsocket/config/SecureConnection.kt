package com.demo.chat.client.rsocket.config

import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.rsocket.transport.netty.client.TcpClientTransport
import org.springframework.beans.factory.annotation.Value
import reactor.netty.tcp.TcpClient

class SecureConnection {

    @Value("$\\{app.unsecure:false\\}")
    var unsecure: Boolean = false

    fun secureUnsecureTransport(): TcpClientTransport =
        TcpClientTransport.create(
            TcpClient.create()
                .host("")
                .port(1)
                .secure { s ->
                    s.sslContext(
                        SslContextBuilder
                            .forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build()
                    )
                })
}