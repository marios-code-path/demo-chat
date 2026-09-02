package com.demo.chat.test.deploy.redis

import com.redis.testcontainers.RedisStackContainer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

/**
 * Boot verification for recall on the Redis vector backend.
 *
 * Key, persistence, index, and secrets all use memory selectors, so no node
 * id claim activates (docs/NODEID-CLAIM.md): a claim needs a redis or
 * cassandra key or persistence selector. The module ships no memory
 * messaging provider, so pubsub uses redis-pubsub. The vector store and the
 * pubsub both talk to the Redis Stack container.
 */
@TestPropertySource(
    properties = [
        "spring.application.name=redis-vector-boot-test",
        "spring.main.web-application-type=reactive",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.server.proto=rsocket",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=redis-pubsub",
        "app.service.core.index=lucene",
        "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite",
        "app.service.composite.auth",
        "app.service.core.vector=redis",
        "app.service.core.embedding=mock",
        "app.controller.message",
        "app.controller.recall",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.consul.config.enabled=false"
    ]
)
@SpringBootTest(classes = [RedisVectorRecallBootTests.BootApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class RedisVectorRecallBootTests {

    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun recallServiceIsActive() {
        Assertions
            .assertThat(context.containsBean("messageRecallService"))
            .isTrue
    }

    @Test
    fun contextLoads() {
    }

    companion object {
        val redisStack = RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG)
        ).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun redisProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.redis.host") { redisStack.host }
            registry.add("spring.redis.port") { redisStack.firstMappedPort.toString() }
            registry.add("redis-topics.host") { redisStack.host }
            registry.add("redis-topics.port") { redisStack.firstMappedPort.toString() }
        }
    }

    /**
     * Mirrors ChatApp (com.demo.chat.ChatApp) — test-only, since chat-deploy
     * is not on this module's classpath.
     */
    @SpringBootApplication(proxyBeanMethods = false, scanBasePackages = ["com.demo.chat.config"])
    class BootApp
}
