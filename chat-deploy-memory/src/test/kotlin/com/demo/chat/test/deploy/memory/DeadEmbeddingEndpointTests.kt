package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.builder.SpringApplicationBuilder
import java.net.ConnectException
import java.net.ServerSocket

/**
 * An unreachable embedding endpoint fails at a different moment for each
 * vector store. EmbeddingModel.dimensions() is the reason. Spring AI can reach
 * the remote service to answer it, and each store asks at a different time.
 *
 * Every launch here points at a closed loopback port, so no test needs an
 * external network or a secret key. A closed loopback port refuses at once.
 *
 * Each launch also sets max-attempts to 1. The connection is refused at once,
 * but the default retry policy makes 10 attempts and waits between them. Ten
 * attempts make nine waits of 2, 10, 50, and then six of 180 seconds. That is
 * 1142 seconds, which is 19 minutes for one call. Measured on 2026-09-14.
 *
 * SpringApplicationBuilder.properties() writes to defaultProperties, which is
 * the lowest precedence source. So each value below is a command line
 * argument. See forward-register.md.
 */
class DeadEmbeddingEndpointTests {

    private fun closedPort(): Int =
        ServerSocket(0).use { socket -> socket.localPort }

    private fun launchArguments(vector: String, port: Int): Array<String> = arrayOf(
        "--app.nodeid=1",
        "--app.key.type=long",
        "--spring.application.name=dead-endpoint-$vector",
        "--app.server.proto=rsocket",
        "--spring.rsocket.server.port=0",
        "--spring.config.additional-location=classpath:/config/logging.yml," +
            "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "--app.service.core.key=memory",
        "--app.service.core.persistence=memory",
        "--app.service.core.index=lucene",
        "--app.service.core.pubsub=memory",
        "--app.service.core.secrets=memory",
        "--app.service.composite=true",
        "--app.service.composite.auth=true",
        "--app.service.security.userdetails=true",
        "--app.service.core.vector=$vector",
        "--app.service.core.embedding=openai",
        "--app.service.core.embedding.identity=dead-endpoint-v1",
        "--app.service.core.embedding.openai.base-url=http://127.0.0.1:$port",
        "--app.service.core.embedding.openai.api-key=not-a-secret",
        "--app.service.core.embedding.openai.model=stub-embedding",
        "--app.service.core.embedding.openai.max-attempts=1",
    )

    @Test
    fun `an unreachable endpoint fails startup under embedded`() {
        // The collection bean takes its width from dimensions(), so the call
        // happens while the context refreshes.
        val port = closedPort()

        val thrown = catchThrowable {
            SpringApplicationBuilder(ChatApp::class.java)
                .run(*launchArguments("embedded", port))
                .close()
        }

        assertReachedTheEndpoint(thrown, port)
    }

    @Test
    fun `an unreachable endpoint starts under simple and fails the first operation`() {
        // SimpleVectorStore never calls dimensions() at build time. So the
        // context refreshes, and the failure waits for a vector operation.
        val port = closedPort()
        val context = SpringApplicationBuilder(ChatApp::class.java)
            .run(*launchArguments("simple", port))

        try {
            assertThat(context.isActive)
                .describedAs("the context must refresh under simple")
                .isTrue()

            val store = context.getBean(VectorStore::class.java)

            val thrown = catchThrowable {
                store.add(
                    listOf(
                        Document.builder()
                            .id("message:long:1")
                            .text("apple pie recipe")
                            .build()
                    )
                )
            }

            assertReachedTheEndpoint(thrown, port)
        } finally {
            context.close()
        }
    }

    /**
     * Asserts that the failure came from the endpoint and from nothing else.
     *
     * A bare `isNotNull` would pass on a missing bean, a bad property name, or
     * a port that another process holds. Each of those is a defect in the
     * test, not the behaviour under test. So this check reads the whole cause
     * chain and requires the closed port number in it, and it refuses a
     * missing bean.
     */
    private fun assertReachedTheEndpoint(thrown: Throwable?, port: Int) {
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
