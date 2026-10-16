package com.demo.chat.test.domain

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.Key
import com.demo.chat.domain.StoreWriteGate
import com.demo.chat.domain.StoreWriteGatePostProcessor
import com.demo.chat.domain.StoreWriteRefusedException
import com.demo.chat.domain.User
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.ProcessLocalIndex
import com.demo.chat.service.core.UserPersistence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.lang.reflect.Proxy
import java.time.Duration

/** The store write gate. See `CHAT-lswjobhz`, plan review item 4. */
class StoreWriteGateTests {

    private val gate = StoreWriteGate(Duration.ofSeconds(2))

    /** A user store whose `add` ends when the test emits on [addDone]. */
    private class SlowUsers : UserPersistence<Long> {
        val addDone: Sinks.Empty<Void> = Sinks.empty()
        val calls = mutableListOf<String>()
        override fun key(): Mono<out Key<Long>> = Mono.empty()
        override fun add(ent: User<Long>): Mono<Void> = Mono.defer { calls.add("add"); addDone.asMono() }
        override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { calls.add("rem") }
        override fun get(key: Key<Long>): Mono<out User<Long>> = Mono.defer { calls.add("get"); Mono.empty() }
        override fun all(): Flux<out User<Long>> = Flux.empty()
    }

    private fun processor() = StoreWriteGatePostProcessor(
        StaticListableBeanFactory(mapOf("storeWriteGate" to gate)).getBeanProvider(StoreWriteGate::class.java)
    )

    @Test
    fun `a write counts from its start to its end`() {
        val users = SlowUsers()
        val gated = processor().postProcessAfterInitialization(users, "userPersistence") as UserPersistence<Long>

        val subscription = gated.add(mock()).subscribe()
        assertThat(gate.active).isEqualTo(1)
        users.addDone.tryEmitEmpty()
        assertThat(gate.active).isEqualTo(0)
        subscription.dispose()
    }

    @Test
    fun `a caller that cancels does not end the count`() {
        val users = SlowUsers()
        val gated = processor().postProcessAfterInitialization(users, "userPersistence") as UserPersistence<Long>

        gated.add(mock()).subscribe().dispose()
        assertThat(gate.active).describedAs("the count after the caller canceled").isEqualTo(1)
        users.addDone.tryEmitEmpty()
        assertThat(gate.active).isEqualTo(0)
    }

    @Test
    fun `a closed gate refuses a write and passes a read`() {
        val users = SlowUsers()
        val gated = processor().postProcessAfterInitialization(users, "userPersistence") as UserPersistence<Long>

        assertThat(gate.closeAndDrain()).isTrue()

        StepVerifier.create(gated.add(mock())).expectError(StoreWriteRefusedException::class.java).verify()
        StepVerifier.create(gated.get(mock())).verifyComplete()
        assertThat(users.calls).containsExactly("get")
    }

    @Test
    fun `the write sees the subscriber context`() {
        val seen = gate.admit {
            Mono.deferContextual { context -> Mono.just(context.getOrDefault("trace", "none")!!) }
        }.contextWrite { it.put("trace", "t-1") }

        StepVerifier.create(seen).expectNext("t-1").verifyComplete()
    }

    @Test
    fun `a drain waits for an active write and times out when it never ends`() {
        val short = StoreWriteGate(Duration.ofMillis(300))
        short.admit { Mono.never<Void>() }.subscribe()
        val started = System.nanoTime()
        assertThat(short.closeAndDrain()).isFalse()
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(300))
        assertThat(short.active).isEqualTo(1)
    }

    @Test
    fun `the processor skips a process local index and a bean that is not a store service`() {
        val local = mock<IndexService<Long, Any, Any>>(extraInterfaces = arrayOf(ProcessLocalIndex::class))
        assertThat(processor().postProcessAfterInitialization(local, "luceneIndex")).isSameAs(local)
        assertThat(processor().postProcessAfterInitialization("text", "name")).isEqualTo("text")
    }

    /** An RSocket controller implements the store interface. It is not a store service. */
    @Controller
    class UserController(that: UserPersistence<Long>) : UserPersistence<Long> by that

    /** A configuration holder. Its `@Bean` services are wrapped one by one, not through it. */
    @Configuration
    open class ConfiguredHolder(that: PersistenceServiceBeans<Long, String>) : PersistenceServiceBeans<Long, String> by that

    private fun holderOf(users: UserPersistence<Long>): PersistenceServiceBeans<Long, String> {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            PersistenceServiceBeans::class.java.classLoader, arrayOf(PersistenceServiceBeans::class.java)
        ) { _, method, _ -> if (method.name == "userPersistence") users else null } as PersistenceServiceBeans<Long, String>
    }

    @Test
    fun `the processor skips a controller bean`() {
        val controller = UserController(SlowUsers())
        assertThat(processor().postProcessAfterInitialization(controller, "userController")).isSameAs(controller)
    }

    @Test
    fun `the processor gates the services that a plain holder returns`() {
        val users = SlowUsers()
        @Suppress("UNCHECKED_CAST")
        val holder = processor().postProcessAfterInitialization(holderOf(users), "cassandraIndexServices") as PersistenceServiceBeans<Long, String>

        val service = holder.userPersistence()
        assertThat(StoreWriteGatePostProcessor.isGated(service)).isTrue()
        service.add(mock()).subscribe()
        assertThat(gate.active).isEqualTo(1)
        users.addDone.tryEmitEmpty()
        assertThat(gate.active).isEqualTo(0)
    }

    @Test
    fun `the processor skips a configuration holder`() {
        val holder = ConfiguredHolder(holderOf(SlowUsers()))
        assertThat(processor().postProcessAfterInitialization(holder, "redisPersistenceServices")).isSameAs(holder)
    }
}
