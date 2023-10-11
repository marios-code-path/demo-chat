package com.demo.chat.client.rsocket.transport

import com.demo.chat.domain.ChatException
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
                try {
                    val keyStore = KeyStore.getInstance("PKCS12").apply {
                        this.load(FileInputStream(keyFile), storePass.toCharArray())
                    }

                    keyStore.getKey("1", storePass.toCharArray())
                    this.init(keyStore, storePass.toCharArray())
                } catch(e: Exception) {
                    throw ChatException("Unable to Load the PKCS12 keyfile at ${keyFile.absolutePath}.")
                }
            }

        val trustManager = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply {
                try {
                    val ks = KeyStore.getInstance("PKCS12").apply {
                        this.load(FileInputStream(trustFile), storePass.toCharArray())
                    }

                    this.init(ks)
                } catch(e: Exception) {
                    throw ChatException("Unable to Load the PKCS12 trust store at ${trustFile.absolutePath}.")
                }

            }

        return SslContextBuilder
            .forClient()
            .keyStoreType("PKCS12")
            .keyManager(keyManager)
            .trustManager(trustManager)
            .build()
    }
}