package com.demo.chat

import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatMessageRoomRepository
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.ChatUserServiceCassandra
import io.rsocket.*
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.WebsocketServerTransport
import io.rsocket.util.DefaultPayload
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@SpringBootApplication
class ChatServiceRsocketApplication

fun main(args: Array<String>) {

    runApplication<ChatServiceRsocketApplication>(*args)
}

@Configuration
@Import(ChatUserRepository::class, ChatMessageRepository::class, ChatMessageRoomRepository::class)
class CassandraRepositoryConfiguration {
    @Bean
    fun userService(userRepo: ChatUserRepository,
                    userHandleRepo: ChatUserHandleRepository) =
            ChatUserServiceCassandra(userRepo, userHandleRepo)

    val port = 9191

    val closeable: Mono<CloseableChannel> = RSocketFactory
            .receive()
            .acceptor { setup, rSocket ->
                handler(setup, rSocket)
            } // server handler RSocket
            .transport(WebsocketServerTransport.create(port))  // Netty websocket transport
            .start()

    private fun handler(setup: ConnectionSetupPayload, rSocket: RSocket): Mono<RSocket> {
        return Mono.just(object : AbstractRSocket() {
            override fun requestStream(payload: Payload): Flux<Payload> {
                return Flux.just(DefaultPayload.create("server handler response"))
            }
        })
    }

}
