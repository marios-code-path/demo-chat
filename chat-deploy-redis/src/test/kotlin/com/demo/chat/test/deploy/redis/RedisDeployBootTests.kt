package com.demo.chat.test.deploy.redis

import org.junit.jupiter.api.Disabled
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
 * DISABLED: with the current classpath the context fails to start with
 * `NoSuchBeanDefinitionException: No qualifying bean of type
 * 'com.demo.chat.config.IndexServiceBeans<?, ?, ?>'` — index=lucene is
 * selected (matchIfMissing) but chat-index-lucene is not a dependency of
 * chat-deploy-redis, and secrets=memory likewise has no provider
 * (chat-persistence-memory absent). This is the known gap the
 * redis-backend profile wiring plan closes
 * (.hermes/plans/2026-08-21_141027-redis-backend-profile-wiring.md,
 * Tasks 1 + 2). Re-enable after those tasks (switching
 * `app.service.core.secrets` to `redis`) — this test is the boot proof
 * for that work.
 *
 * Boot bugs this test caught and that are now fixed in main code:
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
@Disabled
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