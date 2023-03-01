package com.demo.chat.test.rsocket

import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.secure.transport.UnprotectedConnection
import io.rsocket.core.RSocketServer
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import org.springframework.beans.factory.annotation.Autowired
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
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import reactor.core.publisher.Hooks


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
                UnprotectedConnection()
                    .tcpClientTransport("localhost", server.address().port)
            )

    private lateinit var server: CloseableChannel

    @Autowired
    private lateinit var rsocket: RSocketRequester

    @EventListener
    fun handleClosedEvent(event: ContextClosedEvent) {
        rsocket.dispose()
        server.dispose()
    }
}
