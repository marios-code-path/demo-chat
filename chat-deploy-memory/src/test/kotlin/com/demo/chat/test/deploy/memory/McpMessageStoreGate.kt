package com.demo.chat.test.deploy.memory

import com.demo.chat.domain.Message
import com.demo.chat.service.core.MessagePersistence
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MessageStoreGate {
    private val release = AtomicReference<Sinks.One<Void>?>(null)
    private val entered = AtomicReference(CountDownLatch(1))

    fun arm() {
        entered.set(CountDownLatch(1))
        check(release.compareAndSet(null, Sinks.one<Void>()))
    }

    fun beforeAdd(): Mono<Void> = Mono.defer {
        val current = release.get()
        if (current == null) Mono.empty() else {
            entered.get().countDown()
            current.asMono()
        }
    }

    fun awaitEntry(): Boolean = entered.get().await(10, TimeUnit.SECONDS)

    fun open() {
        release.getAndSet(null)?.tryEmitEmpty()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class McpMessageStoreGateConfiguration {
    companion object {
        @Bean
        @JvmStatic
        fun messageStoreGate() = MessageStoreGate()

        @Bean
        @JvmStatic
        fun gatedMessageStore(gate: MessageStoreGate): BeanPostProcessor =
            object : BeanPostProcessor {
                @Suppress("UNCHECKED_CAST")
                override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                    if (beanName != "messagePersistence") return bean
                    val real = bean as MessagePersistence<Long, String>
                    return object : MessagePersistence<Long, String> by real {
                        override fun add(ent: Message<Long, String>): Mono<Void> =
                            gate.beforeAdd().then(Mono.defer { real.add(ent) })
                    }
                }
            }
    }
}
