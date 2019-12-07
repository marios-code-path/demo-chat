package com.demo.chat.chatconsumers

import com.demo.chat.domain.UUIDKey
import com.demo.chat.domain.User
import com.demo.chat.service.UserPersistence
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.ReplayProcessor
import java.time.Duration

class UserPersistenceClientTests {


    private interface UserClientHandler : UserPersistence {
        @MessageMapping("key")
        override fun key(): Mono<out UUIDKey>

        @MessageMapping("add")
        override fun add(ent: User): Mono<Void>

        @MessageMapping("rem")
        override fun rem(key: UUIDKey): Mono<Void>

        @MessageMapping("get")
        override fun get(key: UUIDKey): Mono<out User>

        @MessageMapping("all")
        override fun all(): Flux<out User>

    }
    private class ClientHandler {

        internal val fireForgetPayloads = ReplayProcessor.create<String>()

        @MessageMapping("receive")
        internal fun receive(payload: String) {
            this.fireForgetPayloads.onNext(payload)
        }

        @MessageMapping("echo")
        internal fun echo(payload: String): String {
            return payload
        }

        @MessageMapping("echo-async")
        internal fun echoAsync(payload: String): Mono<String> {
            return Mono.delay(Duration.ofMillis(10)).map { aLong -> "$payload async" }
        }

        @MessageMapping("echo-stream")
        internal fun echoStream(payload: String): Flux<String> {
            return Flux.interval(Duration.ofMillis(10)).map { aLong -> "$payload $aLong" }
        }

        @MessageMapping("echo-channel")
        internal fun echoChannel(payloads: Flux<String>): Flux<String> {
            return payloads.delayElements(Duration.ofMillis(10)).map { payload -> "$payload async" }
        }
    }
}