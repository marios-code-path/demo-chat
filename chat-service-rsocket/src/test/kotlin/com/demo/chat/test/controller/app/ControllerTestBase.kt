package com.demo.chat.test.controller.app

import com.demo.chat.domain.Key
import com.demo.chat.domain.TextMessage
import com.demo.chat.service.UUIDKeyService
import io.rsocket.RSocket
import io.rsocket.RSocketFactory
import io.rsocket.frame.decoder.PayloadDecoder
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import java.util.*


fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T


private val ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
fun randomAlphaNumeric(size: Int): String {
    var count = size
    val builder = StringBuilder()
    while (count-- != 0) {
        val character = (Math.random() * ALPHA_NUMERIC_STRING.length).toInt()
        builder.append(ALPHA_NUMERIC_STRING[character])
    }
    return builder.toString()
}

@ExtendWith(SpringExtension::class)
open class ControllerTestBase {
    private lateinit var socket: RSocket

    lateinit var requestor: RSocketRequester

    @Autowired
    private lateinit var keyService: UUIDKeyService

    private lateinit var server: CloseableChannel

    private var counter = Random().nextInt()

    fun randomMessage(): TextMessage {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        val msg = TextMessage.create(messageId, roomId, userId, "Hello $counter !")
        return msg
    }

    @BeforeEach
    fun setUp(context: ApplicationContext) {
        val messageHandler = context.getBean(RSocketMessageHandler::class.java)

        server = RSocketFactory.receive()
                .frameDecoder(PayloadDecoder.ZERO_COPY)
                .acceptor(messageHandler.responder())
                .transport(TcpServerTransport.create("localhost", 0))
                .start()
                .block()!!

        val strategies = context.getBean(RSocketStrategies::class.java)

        requestor = RSocketRequester
                .builder()
                .rsocketStrategies(strategies)
                .connectTcp("localhost", server.address().port)
                .block()!!

        socket = requestor.rsocket()

        BDDMockito
                .given(keyService.id(String::class.java))
                .willReturn(Mono.just(Key.eventKey(UUID.randomUUID())))

        Hooks.onOperatorDebug()
    }

    @AfterEach
    fun tearDown(@Autowired config: ConfigurationRSocket) {
        requestor.rsocket().dispose()
        server.dispose()
    }
}