package com.demo.chat.client.rsocket.transport

import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory

open class InsecureConnection(override val webSocket: Boolean = false) : SecureRSocketClientTransportFactory {
    override fun getSSLContext(): SslContext = SslContextBuilder
        .forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build()
}