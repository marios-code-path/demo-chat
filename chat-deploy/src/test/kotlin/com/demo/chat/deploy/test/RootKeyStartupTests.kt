package com.demo.chat.deploy.test

import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER

import com.fasterxml.jackson.databind.ObjectMapper

import com.demo.chat.service.core.InitializingKVStore

import com.demo.chat.service.core.StartupIndexLoad
import com.demo.chat.service.core.StoreShapeCheck
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import org.springframework.context.ApplicationListener

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
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.MapPropertySource
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The root key source at startup, through the Spring configuration. A process
 * that needs roots must start with a valid source, or it must not start. See
 * `CHAT-avduuqwp`.
 */
class RootKeyStartupTests {

    /** One instance per test. A failed context cannot answer a bean, so the tests read this one. */
    private val rootKeys = RootKeys<Long>()

    private val base = ApplicationContextRunner()
        .withBean(TypeUtil::class.java, { TypeUtil.LongUtil })
        .withBean(RootKeys::class.java, { rootKeys })
        .withUserConfiguration(DeploymentEventPublisher::class.java, RootKeyInitializationListeners::class.java)

    @Test
    fun `a store and a generator load every root`() {
        base.withBean(RootKeyStore::class.java, { MapStore() }).withBean(IKeyGenerator::class.java, { ids() })
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(rootKeys.domains().keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
            }
    }

    @Test
    fun `a process with no store and no scheme fails the start`() {
        base.run { ctx ->
            assertThat(ctx.startupFailure).hasRootCauseInstanceOf(ChatException::class.java)
                .rootCause().hasMessageContaining("no RootKeyStore")
            assertThat(rootKeys.domains()).isEmpty()
        }
    }

    // T7. The shape check runs before any root is read.
    @Test
    fun `a store of the wrong shape fails the start before any root is read`() {
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val recording = object : RootKeyStore<Long> {
            override fun read(): Mono<Map<ChatDomain, Long>> = Mono.fromCallable { reads.incrementAndGet(); emptyMap() }
            override fun createIfAbsent(domain: ChatDomain, id: Long): Mono<Long> = Mono.fromCallable { reads.incrementAndGet(); id }
        }
        base.withBean(RootKeyStore::class.java, { recording }).withBean(IKeyGenerator::class.java, { ids() })
            .withBean(StoreShapeCheck::class.java, { StoreShapeCheck { throw ChatException("Missing: keys.root. Recreate the store.") } })
            .run { ctx ->
                assertThat(ctx.startupFailure).rootCause().hasMessageContaining("Recreate the store")
                assertThat(reads.get()).isZero()
                assertThat(rootKeys.domains()).isEmpty()
            }
    }

    @Test
    fun `a store of the right shape loads every root`() {
        base.withBean(RootKeyStore::class.java, { MapStore() }).withBean(IKeyGenerator::class.java, { ids() })
            .withBean(StoreShapeCheck::class.java, { StoreShapeCheck { } })
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(rootKeys.domains().keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
            }
    }

    @Test
    fun `a store without a generator fails the start`() {
        base.withBean(RootKeyStore::class.java, { MapStore() }).run { ctx ->
            assertThat(ctx.startupFailure).rootCause().hasMessageContaining("no IKeyGenerator")
        }
    }

    @Test
    fun `a store failure fails the start`() {
        val failing = object : RootKeyStore<Long> {
            override fun read(): Mono<Map<ChatDomain, Long>> = Mono.error(IllegalStateException("store unavailable"))
            override fun createIfAbsent(domain: ChatDomain, id: Long): Mono<Long> = Mono.error(IllegalStateException("store unavailable"))
        }
        base.withBean(RootKeyStore::class.java, { failing }).withBean(IKeyGenerator::class.java, { ids() }).run { ctx ->
            assertThat(ctx.startupFailure).rootCause().hasMessageContaining("store unavailable")
            assertThat(rootKeys.domains()).isEmpty()
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
            assertThat(ctx.startupFailure).rootCause().hasMessageContaining("incomplete")
            assertThat(rootKeys.domains()).isEmpty()
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
    fun `a scheme with spaces or another case fails the context`() {
        listOf(" ", " http ", "http ", " kv", "HTTP", "Kv").forEach { scheme ->
            base.withRaw("app.rootkeys.consume.scheme" to scheme, "app.kv.rootkeys" to "rootkeys", "app.rootkeys.consume.source" to "http://h:1")
                .withBean(RootKeyStore::class.java, { MapStore() }).withBean(IKeyGenerator::class.java, { ids() })
                .run { ctx ->
                    assertThat(ctx).`as`("scheme '$scheme'").hasFailed()
                    assertThat(ctx.startupFailure).rootCause().hasMessageContaining("no spaces")
                }
        }
    }

    @Test
    fun `a required value that is not canonical fails the context`() {
        listOf(" false", "False", "no").forEach { value ->
            base.withRaw("app.rootkeys.required" to value).run { ctx ->
                assertThat(ctx).`as`("required '$value'").hasFailed()
                assertThat(ctx.startupFailure).rootCause().hasMessageContaining("app.rootkeys.required")
            }
        }
    }

    @Test
    fun `the kv scheme without a snapshot name fails the context`() {
        base.withPropertyValues("app.rootkeys.consume.scheme=kv").run { ctx ->
            assertThat(ctx).hasFailed()
            assertThat(ctx.startupFailure).rootCause().hasMessageContaining("app.kv.rootkeys")
        }
    }

    private val wrongShape = StoreShapeCheck { throw ChatException("Missing: auth_metadata.principal_root. Recreate the store.") }

    // T7 review. A snapshot consumer checks its stores before it reads the snapshot.
    @Test
    fun `a kv consumer of the wrong shape fails the start before the snapshot is read`() {
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val kv = object : InitializingKVStore {
            override fun read(name: String): Mono<String> = Mono.fromCallable { reads.incrementAndGet(); "" }
            override fun write(name: String, value: String): Mono<Void> = Mono.empty()
            override fun remove(name: String): Mono<Void> = Mono.empty()
            override fun names(): reactor.core.publisher.Flux<String> =
                reactor.core.publisher.Flux.defer { reads.incrementAndGet(); reactor.core.publisher.Flux.empty() }
        }
        base.withPropertyValues("app.rootkeys.consume.scheme=kv", "app.kv.rootkeys=rootkeys", "app.key.type=long")
            .withBean(InitializingKVStore::class.java, { kv })
            .withBean(StoreShapeCheck::class.java, { wrongShape })
            .run { ctx ->
                assertThat(ctx.startupFailure).rootCause().hasMessageContaining("Recreate the store")
                assertThat(reads.get()).isZero()
                assertThat(rootKeys.domains()).isEmpty()
            }
    }

    // The source is a closed local port. The failure names the shape, so no request was made.
    @Test
    fun `an http consumer of the wrong shape fails the start before the source is asked`() {
        base.withPropertyValues("app.rootkeys.consume.scheme=http", "app.rootkeys.consume.source=http://127.0.0.1:1", "app.key.type=long")
            .withUserConfiguration(HttpRootKeyConsumeOnStart::class.java)
            .withBean(JACKSON_2_OBJECT_MAPPER, ObjectMapper::class.java, { ObjectMapper() })
            .withBean(StoreShapeCheck::class.java, { wrongShape })
            .run { ctx ->
                assertThat(ctx.startupFailure).rootCause().hasMessageContaining("Recreate the store")
                assertThat(rootKeys.domains()).isEmpty()
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
            assertThat(ctx).hasNotFailed()
            assertThat(ctx).hasNotFailed()
            assertThat(rootKeys.domains()).isEmpty()
            assertThatThrownBy { rootKeys.of(ChatDomain.USER) }.hasMessageContaining("not loaded")
        }
    }

    @Test
    fun `the no roots role refuses a consume scheme`() {
        base.withPropertyValues("app.rootkeys.required=false", "app.rootkeys.consume.scheme=http").run { ctx ->
            assertThat(ctx).hasFailed()
            assertThat(ctx.startupFailure).rootCause().hasMessageContaining("app.rootkeys.required=false")
        }
    }

    // CHAT-bafkgkko, D2. The start sequence runs the roots, then every index load, then readiness.
    @Test
    fun `the index load runs after the roots and before readiness`() {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        base.withBean(RootKeyStore::class.java, { MapStore() }).withBean(IKeyGenerator::class.java, { ids() })
            .withBean(StartupIndexLoad::class.java, {
                StartupIndexLoad { Mono.fromRunnable { events += "index roots=${rootKeys.domains().size}" } }
            })
            .withBean("readiness", ApplicationListener::class.java, { readiness(events) })
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(events).containsExactly("index roots=${ChatDomain.entries.size}", "ready")
            }
    }

    // CHAT-bafkgkko. A load that adds one entry and then fails is a partial index. The start fails, and no readiness follows.
    @Test
    fun `a partial index load fails the start and publishes no readiness`() {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val partial = StartupIndexLoad {
            reactor.core.publisher.Flux.just("grant-1", "grant-2")
                .concatMap { entry ->
                    if (entry == "grant-2") Mono.error(IllegalStateException("the auth store failed at grant-2"))
                    else Mono.fromRunnable<Void> { events += "added $entry" }
                }
                .then()
        }
        base.withBean(RootKeyStore::class.java, { MapStore() }).withBean(IKeyGenerator::class.java, { ids() })
            .withBean(StartupIndexLoad::class.java, { partial })
            .withBean("readiness", ApplicationListener::class.java, { readiness(events) })
            .run { ctx ->
                assertThat(ctx).hasFailed()
                // The original error stays the root cause. A ChatException built with a null cause
                // refused initCause, and the original error was lost behind "Can't overwrite cause".
                assertThat(ctx.startupFailure).rootCause()
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessage("the auth store failed at grant-2")
                assertThat(generateSequence(ctx.startupFailure) { it.cause }.mapNotNull { it.message }.joinToString(" | "))
                    .doesNotContain("Can't overwrite cause")
                assertThat(generateSequence(ctx.startupFailure) { it.cause }.mapNotNull { it.message }.joinToString(" | "))
                    .contains("does not start with a partial index")
                assertThat(events).containsExactly("added grant-1")
            }
    }

    // A failed shape check runs no root load and no index load.
    @Test
    fun `a failed shape check runs no index load`() {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        base.withBean(RootKeyStore::class.java, { MapStore() }).withBean(IKeyGenerator::class.java, { ids() })
            .withBean(StoreShapeCheck::class.java, { wrongShape })
            .withBean(StartupIndexLoad::class.java, { StartupIndexLoad { Mono.fromRunnable { events += "index" } } })
            .withBean("readiness", ApplicationListener::class.java, { readiness(events) })
            .run { ctx ->
                assertThat(ctx).hasFailed()
                assertThat(events).isEmpty()
                assertThat(rootKeys.domains()).isEmpty()
            }
    }

    /** `withPropertyValues` trims a value. This helper keeps the raw text, spaces included. */
    private fun ApplicationContextRunner.withRaw(vararg values: Pair<String, String>): ApplicationContextRunner =
        withInitializer { it.environment.propertySources.addFirst(MapPropertySource("raw", mapOf(*values))) }

    /** An anonymous object keeps its generic type, so Spring delivers only the readiness event. */
    private fun readiness(events: MutableList<String>) = object : ApplicationListener<RootKeyInitializationReadyEvent<*>> {
        override fun onApplicationEvent(event: RootKeyInitializationReadyEvent<*>) {
            events += "ready"
        }
    }

    private fun ids(): IKeyGenerator<Long> =
        AtomicLong(100).let { n -> object : IKeyGenerator<Long> { override fun nextId() = n.incrementAndGet() } }

    private class MapStore : RootKeyStore<Long> {
        private val roots = ConcurrentHashMap<ChatDomain, Long>()
        override fun read(): Mono<Map<ChatDomain, Long>> = Mono.fromCallable { roots.toMap() }
        override fun createIfAbsent(domain: ChatDomain, id: Long): Mono<Long> = Mono.fromCallable { roots.putIfAbsent(domain, id) ?: id }
    }
}
