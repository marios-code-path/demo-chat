package com.demo.chat.client.rsocket.transport

import io.netty.handler.ssl.SslContext
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.client.WebsocketClientTransport
import reactor.netty.tcp.TcpClient

interface SecureRSocketClientTransportFactory : RSocketClientTransportFactory {
    fun getSSLContext(): SslContext

    override fun getClientTransport(host: String, port: Int): ClientTransport {
        val tcpClient = TcpClient.create()
            .host(host)
            .port(port)
            .secure { s ->
                s.sslContext(getSSLContext())
            }

        return if(webSocket) {
            WebsocketClientTransport.create(tcpClient)
        } else {

            TcpClientTransport.create(tcpClient)
        }
    }

}