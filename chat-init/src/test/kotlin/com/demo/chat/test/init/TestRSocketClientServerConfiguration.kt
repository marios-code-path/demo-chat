package com.demo.chat.test.init

import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.test.rsocket.TestRequesterFactory
import io.rsocket.RSocket
import io.rsocket.core.RSocketServer
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import reactor.core.publisher.Hooks


@TestConfiguration
class TestRSocketClientServerConfiguration {
    @Value("\${spring.rsocket.server.port:0}")
    lateinit var serverPort: String

    @Bean
    fun rSocketServer(handler: RSocketMessageHandler): RSocketServer = RSocketServer
        .create(handler.responder())

    @Bean
    fun rSocketConnectedServer(rs: RSocketServer): CloseableChannel =
        rs.bind(TcpServerTransport.create("localhost", serverPort.toInt())).block()!!

    @Bean
    fun rSocket(rq: RSocketRequester): RSocket = rq.rsocket()

    @Bean
    fun rSocketRequester(server: CloseableChannel, strategies: RSocketStrategies): RSocketRequester = RSocketRequester
        .builder()
        .rsocketStrategies(strategies)
        .connectTcp("localhost", server.address().port)
        .block()!!

    @Bean
    fun requesterFactory(requester: RSocketRequester): RequesterFactory {
        Hooks.onOperatorDebug()
        return TestRequesterFactory(requester)
    }

    @Autowired
    private lateinit var socket: RSocket

    @Autowired
    private lateinit var server: CloseableChannel

    @EventListener
    fun handleClosedEvent(event: ContextClosedEvent) {
        socket.dispose()
        server.dispose()
    }
}