package com.demo.chat.test.deploy.redis

import com.redis.testcontainers.RedisStackContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import java.net.ConnectException
import java.net.ServerSocket
import java.util.UUID

/**
 * Redis fails at startup only on a first run.
 *
 * Schema initialization skips dimensions() when the index already exists. A
 * new identity creates a new index name, so the first run under a new identity
 * does reach dimensions(). This test uses a fresh identity, so the index is
 * always absent.
 *
 * The launch values come from RedisVectorRecallBootTests, which is the boot
 * test of this backend. That class uses @SpringBootTest, which cannot express
 * a refused context. So this class drives SpringApplicationBuilder instead.
 *
 * The launch sets max-attempts to 1. The connection is refused at once, but
 * the default retry policy makes 10 attempts and waits between them. Ten
 * attempts make nine waits of 2, 10, 50, and then six of 180 seconds. That is
 * 1142 seconds, which is 19 minutes for one call. Measured on 2026-09-14.
 *
 * SpringApplicationBuilder.properties() writes to defaultProperties, the
 * lowest precedence source, so every value below is a command line argument.
 * See forward-register.md.
 *
 * This test claims no node id. Key, persistence, index, and secrets all use
 * memory selectors, so no claim store activates. See docs/NODEID-CLAIM.md and
 * the same note on RedisVectorRecallBootTests.
 */
@Tag("integration")
class RedisDeadEmbeddingEndpointTests {

    companion object {
        val redisStack = RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG)
        ).apply { start() }
    }

    /** Mirrors the BootApp of RedisVectorRecallBootTests. */
    @SpringBootApplication(proxyBeanMethods = false, scanBasePackages = ["com.demo.chat.config"])
    class BootApp

    private fun closedPort(): Int =
        ServerSocket(0).use { socket -> socket.localPort }

    private fun launchArguments(identity: String, port: Int): Array<String> = arrayOf(
        "--spring.application.name=redis-dead-endpoint-test",
        "--spring.main.web-application-type=reactive",
        "--server.port=0",
        "--spring.rsocket.server.port=0",
        "--app.server.proto=rsocket",
        "--app.key.type=long",
        "--app.nodeid=1",
        "--app.service.core.key=memory",
        "--app.service.core.pubsub=redis-pubsub",
        "--app.service.core.index=lucene",
        "--app.service.core.persistence=memory",
        "--app.service.core.secrets=memory",
        "--app.service.composite=true",
        "--app.service.composite.auth=true",
        "--app.service.core.vector=redis",
        "--app.service.core.embedding=openai",
        "--app.service.core.embedding.identity=$identity",
        "--app.service.core.embedding.openai.base-url=http://127.0.0.1:$port",
        "--app.service.core.embedding.openai.api-key=not-a-secret",
        "--app.service.core.embedding.openai.model=stub-embedding",
        "--app.service.core.embedding.openai.max-attempts=1",
        "--app.controller.message=true",
        "--app.controller.recall=true",
        "--spring.redis.host=${redisStack.host}",
        "--spring.redis.port=${redisStack.firstMappedPort}",
        "--redis-topics.host=${redisStack.host}",
        "--redis-topics.port=${redisStack.firstMappedPort}",
        "--spring.cloud.consul.enabled=false",
        "--spring.cloud.consul.discovery.enabled=false",
        "--spring.cloud.consul.config.enabled=false",
    )

    @Test
    fun `an unreachable endpoint fails startup under redis when the index is new`() {
        val identity = "dead-" + UUID.randomUUID().toString().take(8)
        val port = closedPort()

        val thrown = catchThrowable {
            SpringApplicationBuilder(BootApp::class.java)
                .run(*launchArguments(identity, port))
                .close()
        }

        // A bare isNotNull would pass on a missing bean or a bad property
        // name. Each of those is a defect in the test, not the behaviour under
        // test. So this check reads the whole cause chain.
        assertThat(thrown).describedAs("expected a failure").isNotNull()

        val chain = generateSequence(thrown) { it.cause }.toList()
        val text = chain.joinToString(" | ") { "${it.javaClass.name}: ${it.message}" }

        assertThat(chain.map { it.javaClass.name })
            .describedAs("the failure must not be a missing bean: %s", text)
            .doesNotContain("org.springframework.beans.factory.NoSuchBeanDefinitionException")

        // The type, and not only the text. A port number can appear in a
        // message that a live server returned, so the text check alone would
        // pass on an HTTP 500. A refused TCP connection is always a
        // ConnectException. Netty's AnnotatedConnectException extends it, so
        // one check covers every client this repository builds.
        assertThat(chain.any { it is ConnectException })
            .describedAs("the failure must be a refused connection: %s", text)
            .isTrue()

        assertThat(text)
            .describedAs("the failure must name the closed endpoint")
            .contains(port.toString())
    }
}
