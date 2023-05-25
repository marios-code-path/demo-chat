package com.demo.chat.client.rsocket.transport

import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

class PKCS12ClientConnection(
    private val trustFile: File,
    private val keyFile: File,
    private val storePass: String,
    override val webSocket: Boolean = false
) : SecureRSocketClientTransportFactory {

    override fun getSSLContext(): SslContext {
        val keyManager = KeyManagerFactory
            .getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply {
                val keyStore = KeyStore.getInstance("PKCS12").apply {
                    this.load(FileInputStream(keyFile), storePass.toCharArray())

                }
                keyStore.getKey("1", storePass.toCharArray())
                this.init(keyStore, storePass.toCharArray())

            }

        val trustManager = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply {
                val ks = KeyStore.getInstance("PKCS12").apply {
                    this.load(FileInputStream(trustFile), storePass.toCharArray())
                }
                this.init(ks)
            }

        return SslContextBuilder
            .forClient()
            .keyStoreType("PKCS12")
            .keyManager(keyManager)
            .trustManager(trustManager)
            .build()
    }
}