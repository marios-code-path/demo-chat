package com.demo.chat.test.init

import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.test.rsocket.TestRequesterFactory
import io.rsocket.RSocket
import io.rsocket.RSocketFactory
import io.rsocket.frame.decoder.PayloadDecoder
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import reactor.core.publisher.Hooks

@TestConfiguration
class TestClientServerConfiguration {
    lateinit var socket: RSocket

    lateinit var requester: RSocketRequester

    lateinit var server: CloseableChannel       // using.. local.rsocket.server.port ?

    @Value("\${spring.rsocket.server.port:0}")
    lateinit var serverPort: String

    @Bean
    fun requesterFactory(context: ApplicationContext): RequesterFactory {
        val messageHandler = context.getBean(RSocketMessageHandler::class.java)

        server = RSocketFactory.receive()
            .frameDecoder(PayloadDecoder.ZERO_COPY)
            .acceptor(messageHandler.responder())
            .transport(TcpServerTransport.create("localhost", serverPort.toInt()))
            .start()
            .block()!!

        val strategies = context.getBean(RSocketStrategies::class.java)

        requester = RSocketRequester
            .builder()
            .rsocketStrategies(strategies)
            .connectTcp("localhost", server.address().port)
            .block()!!

        socket = requester.rsocket()

        Hooks.onOperatorDebug()

        return TestRequesterFactory(requester)
    }

    @EventListener
    fun handleClosedEvent(event: ContextClosedEvent) {
        socket.dispose()
        server.dispose()
    }
}