package com.demo.chat.chatconsumers

import io.rsocket.transport.netty.client.TcpClientTransport
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootApplication
class ChatConsumersApplication {

    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)
    val latch: CountDownLatch = CountDownLatch(1)

}

fun main(args: Array<String>) {
    runApplication<ChatConsumersApplication>(*args)
}

@Configuration
class ClientRSocketModule {

    @Bean
    fun userControllerRequestor(builder: RSocketRequester.Builder): RSocketRequester? = builder.connect(TcpClientTransport.create(7070)).block()

}