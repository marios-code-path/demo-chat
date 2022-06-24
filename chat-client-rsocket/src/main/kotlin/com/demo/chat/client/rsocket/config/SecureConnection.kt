package com.demo.chat.client.rsocket.config

import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.rsocket.transport.netty.client.TcpClientTransport
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import reactor.netty.tcp.TcpClient

open class SecureConnection {
//    TODO move the secure flag to the app arguments
//    @Value("\${app.unsecure:false}")
//    var unsecure: Boolean = false

    fun tcpClientTransport(host: String, port: Int, secure: Boolean): TcpClientTransport =
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