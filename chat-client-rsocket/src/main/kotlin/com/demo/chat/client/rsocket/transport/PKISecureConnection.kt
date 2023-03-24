package com.demo.chat.client.rsocket.transport

import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.netty.tcp.TcpClient
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

class PKISecureConnection(private val trustFile: File,
                          private val keyFile: File,
                          private val jksPass: String) : RSocketClientTransportFactory {

    override fun tcpClientTransport(host: String, port: Int): TcpClientTransport {

        val keyManager =  KeyManagerFactory
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

        return TcpClientTransport.create(
            TcpClient.create()
                .host(host)
                .port(port)
                .secure { s ->
                    s.sslContext(
                        SslContextBuilder
                            .forClient()
                            .keyStoreType("JKS")
                            .keyManager(keyManager)
                            .trustManager(trustManager)
                            .build()
                    )
                })
    }
}