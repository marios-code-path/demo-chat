package com.demo.chat.test.rsocket

import io.rsocket.RSocket
import io.rsocket.RSocketFactory
import io.rsocket.frame.decoder.PayloadDecoder
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T

@ExtendWith(SpringExtension::class)
open class RSocketTestBase {
    lateinit var socket: RSocket

    lateinit var requester: RSocketRequester

    lateinit var server: CloseableChannel       // using.. local.rsocket.server.port ?

    @Value("\${spring.rsocket.server.port:0}")
    lateinit var serverPort: String

    @BeforeEach
    fun setUp(context: ApplicationContext) {
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
    }

    @AfterEach
    fun tearDown() {
        socket.dispose()
        server.dispose()
    }
}