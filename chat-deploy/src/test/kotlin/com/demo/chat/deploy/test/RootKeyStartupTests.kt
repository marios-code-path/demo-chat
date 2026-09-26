package com.demo.chat.deploy.test

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.config.deploy.init.HttpRootKeyConsumeOnStart
import com.demo.chat.config.deploy.init.RootKeyInitializationListeners
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.RootKeyStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The root key source at startup, through the Spring configuration. A process
 * that needs roots must start with a valid source, or it must not start. See
 * `CHAT-avduuqwp`.
 */
class RootKeyStartupTests {

    private val base = ApplicationContextRunner()
        .withBean(TypeUtil::class.java, { TypeUtil.LongUtil })
        .withBean(RootKeys::class.java, { RootKeys<Long>() })
        .withUserConfiguration(DeploymentEventPublisher::class.java, RootKeyInitializationListeners::class.java)

    @Test
    fun `a store and a generator load every root`() {
        base.withBean(RootKeyStore::class.java, { MapStore() }).withBean(IKeyGenerator::class.java, { ids() })
            .run { ctx ->
                start(ctx)
                assertThat(roots(ctx).domains().keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
            }
    }

    @Test
    fun `a process with no store and no scheme fails the start`() {
        base.run { ctx ->
            assertThatThrownBy { start(ctx) }.hasRootCauseInstanceOf(ChatException::class.java)
                .rootCause().hasMessageContaining("no RootKeyStore")
            assertThat(roots(ctx).domains()).isEmpty()
        }
    }

    @Test
    fun `a store without a generator fails the start`() {
        base.withBean(RootKeyStore::class.java, { MapStore() }).run { ctx ->
            assertThatThrownBy { start(ctx) }.rootCause().hasMessageContaining("no IKeyGenerator")
        }
    }

    @Test
    fun `a store failure fails the start`() {
        val failing = object : RootKeyStore<Long> {
            override fun read(): Mono<Map<ChatDomain, Long>> = Mono.error(IllegalStateException("store unavailable"))
            override fun createIfAbsent(domain: ChatDomain, id: Long): Mono<Long> = Mono.error(IllegalStateException("store unavailable"))
        }
        base.withBean(RootKeyStore::class.java, { failing }).withBean(IKeyGenerator::class.java, { ids() }).run { ctx ->
            assertThatThrownBy { start(ctx) }.hasMessageContaining("store unavailable")
            assertThat(roots(ctx).domains()).isEmpty()
        }
    }

    @Test
    fun `a store that answers no root for a domain fails the start`() {
        val incomplete = object : RootKeyStore<Long> {
            override fun read(): Mono<Map<ChatDomain, Long>> = Mono.just(emptyMap())
            override fun createIfAbsent(domain: ChatDomain, id: Long): Mono<Long> =
                if (domain == ChatDomain.FRANKING_TAG) Mono.empty() else Mono.just(id)
        }
        base.withBean(RootKeyStore::class.java, { incomplete }).withBean(IKeyGenerator::class.java, { ids() }).run { ctx ->
            assertThatThrownBy { start(ctx) }.rootCause().hasMessageContaining("incomplete")
            assertThat(roots(ctx).domains()).isEmpty()
        }
    }

    @Test
    fun `an unsupported scheme fails the context, with a store or without one`() {
        base.withPropertyValues("app.rootkeys.consume.scheme=invalid-scheme")
            .withBean(RootKeyStore::class.java, { MapStore() }).withBean(IKeyGenerator::class.java, { ids() })
            .run { ctx ->
                assertThat(ctx).hasFailed()
                assertThat(ctx.startupFailure).rootCause().hasMessageContaining("invalid-scheme")
            }
        base.withPropertyValues("app.rootkeys.consume.scheme=invalid-scheme").run { ctx ->
            assertThat(ctx).hasFailed()
        }
    }

    @Test
    fun `the kv scheme without a snapshot name fails the context`() {
        base.withPropertyValues("app.rootkeys.consume.scheme=kv").run { ctx ->
            assertThat(ctx).hasFailed()
            assertThat(ctx.startupFailure).rootCause().hasMessageContaining("app.kv.rootkeys")
        }
    }

    @Test
    fun `the http scheme without a source fails the context`() {
        base.withPropertyValues("app.rootkeys.consume.scheme=http", "app.key.type=long")
            .withUserConfiguration(HttpRootKeyConsumeOnStart::class.java)
            .run { ctx ->
                assertThat(ctx).hasFailed()
                assertThat(ctx.startupFailure).rootCause().hasMessageContaining("app.rootkeys.consume.source")
            }
    }

    @Test
    fun `the no roots role starts and loads nothing`() {
        base.withPropertyValues("app.rootkeys.required=false").run { ctx ->
            start(ctx)
            assertThat(ctx).hasNotFailed()
            assertThat(roots(ctx).domains()).isEmpty()
            assertThatThrownBy { roots(ctx).of(ChatDomain.USER) }.hasMessageContaining("not loaded")
        }
    }

    @Test
    fun `the no roots role refuses a consume scheme`() {
        base.withPropertyValues("app.rootkeys.required=false", "app.rootkeys.consume.scheme=http").run { ctx ->
            assertThat(ctx).hasFailed()
            assertThat(ctx.startupFailure).rootCause().hasMessageContaining("app.rootkeys.required=false")
        }
    }

    private fun start(ctx: AssertableApplicationContext) =
        ctx.publishEvent(ApplicationStartedEvent(SpringApplication(), arrayOf(), ctx.sourceApplicationContext, Duration.ZERO))

    @Suppress("UNCHECKED_CAST")
    private fun roots(ctx: AssertableApplicationContext): RootKeys<Long> = ctx.getBean(RootKeys::class.java) as RootKeys<Long>

    private fun ids(): IKeyGenerator<Long> =
        AtomicLong(100).let { n -> object : IKeyGenerator<Long> { override fun nextId() = n.incrementAndGet() } }

    private class MapStore : RootKeyStore<Long> {
        private val roots = ConcurrentHashMap<ChatDomain, Long>()
        override fun read(): Mono<Map<ChatDomain, Long>> = Mono.fromCallable { roots.toMap() }
        override fun createIfAbsent(domain: ChatDomain, id: Long): Mono<Long> = Mono.fromCallable { roots.putIfAbsent(domain, id) ?: id }
    }
}
