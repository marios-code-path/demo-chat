package com.demo.chat.client.rsocket.transport

import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

class JKSSecureConnection(
    private val trustFile: File,
    private val keyFile: File,
    private val jksPass: String,
    override val webSocket: Boolean = false
) : SecureRSocketClientTransportFactory {

    override fun getSSLContext(): SslContext {
        val keyManager = KeyManagerFactory
            .getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply {
                val keyStore = KeyStore.getInstance("JKS").apply {
                    this.load(FileInputStream(keyFile), jksPass.toCharArray())
                }
                this.init(keyStore, jksPass.toCharArray())
            }

        val trustManager = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply {
                val ks = KeyStore.getInstance("JKS").apply {
                    this.load(FileInputStream(trustFile), jksPass.toCharArray())
                }
                this.init(ks)
            }

        return SslContextBuilder
            .forClient()
            .keyStoreType("JKS")
            .keyManager(keyManager)
            .trustManager(trustManager)
            .build()
    }
}