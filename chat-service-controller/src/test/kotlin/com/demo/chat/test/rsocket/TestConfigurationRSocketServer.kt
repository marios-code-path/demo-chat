package com.demo.chat.test.rsocket

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import io.rsocket.core.RSocketServer
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.client.WebsocketClientTransport
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.transport.netty.server.WebsocketServerTransport
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.*
import org.springframework.boot.autoconfigure.security.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.rsocket.context.RSocketServerBootstrap
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.rsocket.server.LocalRSocketServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered.LOWEST_PRECEDENCE
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import org.springframework.test.context.ContextConfiguration
import reactor.core.publisher.Hooks
import reactor.netty.tcp.TcpClient


class TestModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

@TestConfiguration
@Import(
    TestModules::class,
    JacksonAutoConfiguration::class,
    RSocketSecurityAutoConfiguration::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    RSocketRequesterAutoConfiguration::class
)
open class TestConfigurationRSocketServer(private val isWebSocketEnabled: Boolean = false) {

    init {
        Hooks.onOperatorDebug()
    }

//
//    @Bean
//    fun rSocketServer(handler: RSocketMessageHandler): RSocketServer = RSocketServer
//        .create(handler.responder())
//
//    fun getServerTransport(webSocket: Boolean = false): ServerTransport<CloseableChannel> {
//        return if (webSocket)
//            WebsocketServerTransport.create("localhost", serverPort.toInt())
//        else
//            TcpServerTransport.create("localhost", serverPort.toInt())
//    }
//
    fun getClientTransport(tcpClient: TcpClient, webSocket: Boolean = false): ClientTransport {
        return if (webSocket)
            WebsocketClientTransport.create(tcpClient)
        else
            TcpClientTransport.create(tcpClient)
    }

//    @Bean
//    fun rSocketConnectedServer(rs: RSocketServer): CloseableChannel =
//        rs.bind(getServerTransport(isWebSocketEnabled)).block()!!
//            .apply { server = this }

//    @Bean
//    fun rSocketRequester(server: CloseableChannel, builder: RSocketRequester.Builder): RSocketRequester =
//        builder
//            .transport(
//                getClientTransport(
//                    TcpClient.create()
//                        .host("localhost")
//                        .port(server.address().port),
//                    isWebSocketEnabled
//                )
//            )
//
//    private lateinit var server: CloseableChannel
//
//    @EventListener
//    fun handleClosedEvent(event: ContextClosedEvent) {
//        server.dispose()
//    }
}