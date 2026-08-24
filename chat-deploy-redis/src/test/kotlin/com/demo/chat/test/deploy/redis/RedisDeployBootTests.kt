package com.demo.chat.test.deploy.redis

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration

/**
 * Boot verification for the Redis deploy classpath.
 *
 * Boots the same config surface as ChatApp (scanBasePackages =
 * "com.demo.chat.config") with the standard run-core.sh launch flags
 * (key=redis, pubsub=redis-pubsub, index=lucene, persistence=redis,
 * secrets=memory, composite + auth, all controllers) against a
 * Testcontainers Redis.
 *
 * Enabled. It was disabled while the classpath could not satisfy those
 * flags, and re-enabling it is what drove the six gaps below out into the
 * open, one boot failure at a time. Keep it enabled: it is the only thing
 * standing between this backend and silently rotting again.
 *
 * Gaps it exposed, in the order the context hit them:
 * 1. secrets=memory had no provider — chat-persistence-memory absent.
 * 2. index=lucene had no provider — chat-index-lucene absent.
 * 3. Memory and redis both ship a `KeyGenConfiguration`, ungated, sharing
 *    a simple class name. With both on one classpath the context failed on
 *    a conflicting bean definition. Each is now gated on the key selector.
 * 4. EmptyMessageUtil had no provider — chat-deploy absent, so this
 *    composition root was missing the shared deploy layer entirely.
 * 5. RequestToQueryConverters had no provider — every other backend module
 *    has an AppConfiguration supplying it and this one had none.
 * 6. chat-client-rsocket was on the classpath of a server deployment,
 *    which made `app.rsocket.transport.security.type` mandatory. Nothing
 *    needed the client; it is gone, and chat-service-composite, which had
 *    been arriving through it, is now declared directly.
 *
 * Boot bugs this test caught earlier and that are fixed in main code:
 * 1. ConfigurationPropertiesRedisTopics declared @ConstructorBinding on a
 *    constructor with default arguments — Kotlin copies the annotation
 *    onto the synthetic no-args constructor and the binder rejects it
 *    ("declares @ConstructorBinding on a no-args constructor").
 * 2. RedisConfiguration.redisConnection() declared its return type as
 *    ReactiveRedisConnectionFactory, so Spring Boot's
 *    LettuceConnectionConfiguration (@ConditionalOnMissingBean(
 *    RedisConnectionFactory.class)) did not back off and a second
 *    factory bean made the by-type injection ambiguous. The bean now
 *    declares LettuceConnectionFactory.
 */
@TestPropertySource(
    properties = [
        // chat-build always passes this; the deployment event listeners read it.
        "spring.application.name=redis-boot-test",
        "spring.main.web-application-type=reactive",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.server.proto=rsocket",
        "app.key.type=long",
        "app.service.core.key=redis",
        "app.service.core.pubsub=redis-pubsub",
        "app.service.core.index=lucene",
        "app.service.core.persistence=redis",
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
        "spring.cloud.consul.config.enabled=false"
    ]
)
@SpringBootTest(classes = [RedisDeployBootTests.BootApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class RedisDeployBootTests {

    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun contextLoads() {
        // Reaching here means the full Redis backend wired and started.
    }

    companion object {
        val redis = GenericContainer<Nothing>("redis:5.0.14")
            .apply {
                withExposedPorts(6379)
                waitingFor(
                    LogMessageWaitStrategy()
                        .withRegEx(".*Ready to accept connections.*\\s")
                        .withStartupTimeout(Duration.ofSeconds(60))
                )
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun redisProps(registry: DynamicPropertyRegistry) {
            registry.add("redis-topics.host") { redis.containerIpAddress }
            registry.add("redis-topics.port") { redis.getMappedPort(6379).toString() }
        }
    }

    /**
     * Mirrors ChatApp (com.demo.chat.ChatApp) — test-only, since chat-deploy
     * is not on this module's classpath.
     */
    @SpringBootApplication(proxyBeanMethods = false, scanBasePackages = ["com.demo.chat.config"])
    class BootApp
}