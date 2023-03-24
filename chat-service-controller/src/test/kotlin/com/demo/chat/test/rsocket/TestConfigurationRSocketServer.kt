package com.demo.chat.test.rsocket

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import io.rsocket.core.RSocketServer
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import reactor.core.publisher.Hooks
import reactor.netty.tcp.TcpClient


class TestModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

@TestConfiguration
@Import(
    TestModules::class,
    JacksonAutoConfiguration::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    RSocketRequesterAutoConfiguration::class
)
class TestConfigurationRSocketServer {
    init {
        Hooks.onOperatorDebug()
    }

    @Value("\${spring.rsocket.server.port:0}")
    lateinit var serverPort: String

    @Bean
    fun rSocketServer(handler: RSocketMessageHandler): RSocketServer = RSocketServer
        .create(handler.responder())

    @Bean
    fun rSocketConnectedServer(rs: RSocketServer): CloseableChannel =
        rs.bind(TcpServerTransport.create("localhost", serverPort.toInt())).block()!!
            .apply { server = this }

    @Bean
    fun rSocketRequester(server: CloseableChannel, builder: RSocketRequester.Builder): RSocketRequester =
        builder
            .transport(
                TcpClientTransport.create(
                    TcpClient.create()
                        .host("localhost")
                        .port(server.address().port)
                )
            )

    private lateinit var server: CloseableChannel


    @EventListener
    fun handleClosedEvent(event: ContextClosedEvent) {
        server.dispose()
    }
}
