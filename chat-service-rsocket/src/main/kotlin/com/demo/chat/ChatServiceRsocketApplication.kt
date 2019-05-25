package com.demo.chat

import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.ChatUserServiceCassandra
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.rsocket.*
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.WebsocketServerTransport
import io.rsocket.util.DefaultPayload
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import java.time.Instant

@SpringBootApplication
class ChatServiceRsocketApplication

fun main(args: Array<String>) {

    runApplication<ChatServiceRsocketApplication>(*args)
}

@Configuration
class ChatServiceModule {
    @Bean
    fun userService(userRepo: ChatUserRepository,
                    userHandleRepo: ChatUserHandleRepository): ChatUserServiceCassandra =
            ChatUserServiceCassandra(userRepo, userHandleRepo)
}

@Controller
class UserController(val userService: ChatUserServiceCassandra) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("user-handle")
    fun findByHandle(userReq: UserRequest): Mono<UserResponse> {
        return userService.getUser(userReq.userHandle)
                .map {
                    UserResponse(it)
                }
    }

    @MessageMapping("user-create")
    fun createNewUser(userReq: UserCreateRequest): Mono<UserResponse> {
        return userService.createUser(userReq.name, userReq.userHandle)
                .map {
                    UserResponse(
                            ChatUser(ChatUserKey(it.userId, it.handle),
                                    userReq.name, Instant.now()))
                }
    }
}


@Deprecated("Use the Controller from now on.")
class ChatRsocketUserServiceRunnable(val userService: ChatUserServiceCassandra) {
    val logger: Logger = LoggerFactory.getLogger("UserRSocket")

    val port = 9191
    val mapper = ObjectMapper().registerModule(KotlinModule()).apply {
        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        //configure(SerializationFeature.WRAP_ROOT_VALUE, true)
    }.findAndRegisterModules()!!

    val closeable: Mono<CloseableChannel> = RSocketFactory
            .receive()
            .acceptor { setup, rSocket ->
                handler(setup, rSocket)
            } // server handler RSocket
            .transport(WebsocketServerTransport.create(port))  // Netty websocket transport
            .start()

    private fun handler(setup: ConnectionSetupPayload, rSocket: RSocket): Mono<RSocket> {
        return Mono.just(object : AbstractRSocket() {
            override fun requestResponse(payload: Payload): Mono<Payload> {
                val userHandle = payload?.dataUtf8
                println("userHandle :$userHandle")

                return Mono.just(DefaultPayload.create("Return data"))
            }
        })
    }

    @Bean
    fun startRSocket(): ApplicationRunner = ApplicationRunner {
        logger.info("START")
        closeable.block()
        logger.warn("STOPPED")
    }
}