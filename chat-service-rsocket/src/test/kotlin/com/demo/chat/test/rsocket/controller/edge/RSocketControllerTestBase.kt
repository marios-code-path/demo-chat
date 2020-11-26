package com.demo.chat.test.rsocket.controller.edge

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.IKeyService
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
open class RSocketControllerTestBase {
    private lateinit var socket: RSocket

    lateinit var requester: RSocketRequester

    @Autowired
    private lateinit var keyService: IKeyService<UUID>

    private lateinit var server: CloseableChannel

    private var counter = Random().nextInt()

    fun randomMessage(): Message<UUID, String> {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        return Message.create(MessageKey.create(messageId, roomId, userId), "Hello $counter !", true)
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

        requester = RSocketRequester
                .builder()
                .rsocketStrategies(strategies)
                .connectTcp("localhost", server.address().port)
                .block()!!

        socket = requester.rsocket()

        BDDMockito
                .given(keyService.key(String::class.java))
                .willReturn(Mono.just(Key.funKey(UUID.randomUUID())))

        Hooks.onOperatorDebug()
    }

    @AfterEach
    fun tearDown(@Autowired config: MockCoreServicesConfiguration) {
        requester.rsocket().dispose()
        server.dispose()
    }
}