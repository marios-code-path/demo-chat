package com.demo.chat.client.rsocket.config

import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.rsocket.transport.netty.client.TcpClientTransport
import org.springframework.beans.factory.annotation.Value
import reactor.netty.tcp.TcpClient

class SecureConnection {   // This stream is going down in 3 minutes!!!!! Audio Jitter - laptop uptime is
                            // 70 DAYS!!!!!!!! This is gonna hurt my computer a little bit

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