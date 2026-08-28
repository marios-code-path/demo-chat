package com.demo.chat.test.deploy.redis

import com.demo.chat.domain.NodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Boot level tests for the redis claim seam.
 *
 * Node ids 11 and 12 belong to this class. See the allocation table in the
 * plan. RedisDeployBootTests holds node id 1 in the same container.
 *
 * The foreign claim is written straight to redis, not through a booted
 * context. A guarded context would claim the id itself and then release it
 * on close, and the duplicate would never be present for the second boot.
 */
@Tag("integration")
class RedisClaimBootTests {

    private val container = RedisDeployBootTests.redis

    private fun template(): ReactiveStringRedisTemplate {
        val factory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(container.containerIpAddress, container.getMappedPort(6379))
        ).apply { afterPropertiesSet() }
        return ReactiveStringRedisTemplate(factory)
    }

    private fun seedForeignClaim(nodeId: Int, owner: String) {
        template().delete("chat:nodeclaim:long:$nodeId").block()
        val applied = template().opsForValue()
            .setIfAbsent("chat:nodeclaim:long:$nodeId", owner, Duration.ofSeconds(120))
            .block()
        Assertions.assertEquals(true, applied, "the foreign claim seed must be applied")
    }

    // The launch surface of RedisDeployBootTests. Each test overrides only
    // what it needs after this list.
    private fun launchProperties(): Array<String> = arrayOf(
        "spring.application.name=redis-claim-boot-test",
        "spring.main.web-application-type=reactive",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.server.proto=rsocket",
        "app.key.type=long",
        "app.service.core.pubsub=redis-pubsub",
        "app.service.core.index=lucene",
        "app.service.core.secrets=memory",
        "app.service.composite",
        "app.service.composite.auth",
        "app.controller.persistence",
        "app.controller.index",
        "app.controller.key",
        "app.controller.pubsub",
        "app.controller.secrets",
        "app.controller.user",
        "app.controller.topic",
        "app.controller.message",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        // SpringApplicationBuilder does not read @DynamicPropertySource.
        "redis-topics.host=${container.containerIpAddress}",
        "redis-topics.port=${container.getMappedPort(6379)}"
    )

    private fun boot(vararg extra: String, ready: AtomicBoolean) =
        SpringApplicationBuilder(RedisDeployBootTests.BootApp::class.java)
            .web(WebApplicationType.REACTIVE)
            .properties(*launchProperties(), *extra)
            .listeners(ApplicationListener<ApplicationReadyEvent> { ready.set(true) })

    private fun allMessages(thrown: Throwable): String =
        generateSequence(thrown) { it.cause }.mapNotNull { it.message }.joinToString(" | ")

    @Test
    fun `a live claim fails startup with the actionable message`() {
        seedForeignClaim(11, "foreign-owner@host-z:1#deadbeef")
        val ready = AtomicBoolean(false)

        val thrown = Assertions.assertThrows(Exception::class.java) {
            boot(
                "app.nodeid=11",
                "app.service.core.key=redis",
                "app.service.core.persistence=redis",
                ready = ready
            ).run().close()
        }

        val text = allMessages(thrown)
        Assertions.assertTrue(text.contains("app.nodeid=11 is already claimed"), text)
        Assertions.assertTrue(text.contains("redis store for key type long"), text)
        Assertions.assertTrue(text.contains("foreign-owner@host-z:1#deadbeef"), text)
        // The requirement is failure before ready and before normal traffic.
        Assertions.assertFalse(ready.get(), "ApplicationReadyEvent must not be published")
    }

    @Test
    fun `a memory key selector with redis persistence still claims`() {
        val ready = AtomicBoolean(false)

        boot(
            "app.nodeid=12",
            "app.service.core.key=memory",
            "app.service.core.persistence=redis",
            ready = ready
        ).run().use { context ->
            val stores = context.getBeansOfType(NodeIdClaimStore::class.java)
            Assertions.assertEquals(1, stores.size)
            Assertions.assertEquals("redis", stores.values.first().backendName)
        }

        Assertions.assertTrue(ready.get())
    }
}
