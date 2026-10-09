package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder

/** Cases 23, 24, and 27 of the spec, at startup of a real composition. */
class CommandBusStartupTests {
    private fun launch(vararg extra: String) = SpringApplicationBuilder(ChatApp::class.java).run(
        "--spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "--spring.application.name=command-bus-startup", "--app.server.proto=rsocket", "--server.port=0",
        "--spring.rsocket.server.port=0", "--app.key.type=long", "--app.nodeid=1",
        "--app.service.core.key=memory", "--app.service.core.pubsub=memory", "--app.service.core.index=lucene",
        "--app.service.core.persistence=memory", "--app.service.core.secrets=memory",
        "--app.service.composite", "--app.service.composite.auth=true", "--app.controller.message",
        "--app.service.security.userdetails", "--app.users.create=true", *extra,
    )

    private fun refusal(vararg extra: String): String {
        val thrown = catchThrowable { launch(*extra).close() }
        assertThat(thrown).describedAs("expected a refused startup").isNotNull()
        return generateSequence(thrown) { it.cause }.joinToString(" | ") { it.message ?: "" }
    }

    @Test
    fun `case 23 - an unset bus refuses startup`() {
        assertThat(refusal()).contains("app.command.bus is not set")
    }

    @Test
    fun `case 23 - kafka refuses startup and names Stage 2`() {
        assertThat(refusal("--app.command.bus=kafka")).contains("Stage 2")
    }

    @Test
    fun `case 13 - a finite ttl refuses startup`() {
        assertThat(refusal("--app.command.bus=memory", "--app.command.ttl=10m")).contains("app.command.ttl=10m")
    }

    @Test
    fun `case 24 - a declared replica count above one refuses startup`() {
        assertThat(refusal("--app.command.bus=memory", "--app.command.replicas=2")).contains("does not detect other processes")
    }

    @Test
    fun `case 27 - memory with no vector provider starts`() {
        launch("--app.command.bus=memory").use { context ->
            assertThat(context.isActive).isTrue()
        }
    }
}
