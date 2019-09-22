package com.demo.chat.test

import com.demo.chat.RSocketTestConfig
import com.demo.chat.domain.EventKey
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.KeyService
import com.demo.chat.service.TextMessagePersistence
import io.rsocket.RSocket
import io.rsocket.RSocketFactory
import io.rsocket.frame.decoder.PayloadDecoder
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.messaging.rsocket.MessageHandlerAcceptor
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import java.util.*

@ExtendWith(SpringExtension::class)
open class RSocketTestBase {
    private lateinit var socket: RSocket

    lateinit var requestor: RSocketRequester

    @Autowired
    private lateinit var keyService: KeyService

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
        val messageHandler = context.getBean(MessageHandlerAcceptor::class.java)

        server = RSocketFactory.receive()
                .frameDecoder(PayloadDecoder.ZERO_COPY)
                .acceptor(messageHandler)
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
                .given(keyService.id())
                .willReturn(Mono.just(EventKey.create(UUID.randomUUID())))

        Hooks.onOperatorDebug()
    }

    @AfterEach
    fun tearDown(@Autowired config: RSocketTestConfig) {
        requestor.rsocket().dispose()
        server.dispose()
    }
}