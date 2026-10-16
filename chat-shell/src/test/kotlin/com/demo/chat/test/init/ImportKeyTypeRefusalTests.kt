package com.demo.chat.test.init

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.boot.SpringApplication
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A shell launch with `app.message.import` and a long key type refuses at
 * startup. See `CHAT-zxrgxdlx`, task 7.
 *
 * The test starts [ChatApp], as the shell launch and [ShellIntegrationTestBase]
 * do. Its scan of `com.demo.chat.config` registers the key-type check. The
 * refusal comes before any singleton, so the test needs no container.
 *
 * The expected text is literal. A lookup in `KeyTypeValidation.REQUIREMENTS`
 * would fail at setup when the row is gone, and hide the missed refusal.
 */
class ImportKeyTypeRefusalTests {

    private val launch = arrayOf(
        "app.client.protocol=rsocket", "app.primary=test",
        "app.rootkeys.consume.scheme=http", "app.client.discovery=local",
        "app.rsocket.transport.security.type=unprotected", "app.client.rsocket.core.key",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.composite.user", "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic", "app.service.composite.auth=true",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "app.service.security.userdetails",
        "spring.cloud.consul.config.enabled=false", "spring.rsocket.server.port=0", "server.port=0",
        "spring.shell.interactive.enabled=false", "management.endpoints.enabled-by-default=false",
        "spring.profiles.active=shell",
    )

    @BeforeEach
    fun resetSentinel() {
        Sentinel.built.set(0)
    }

    /** Command-line arguments, so no property file of the launch can replace them. */
    private fun start(vararg extra: String) =
        SpringApplication.run(arrayOf(ChatApp::class.java, Sentinel::class.java), (launch + extra).map { "--$it" }.toTypedArray())

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `a long shell launch with the import switch refuses before any singleton is built`() {
        assertThatThrownBy { start("app.message.import=true", "app.key.type=long").close() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("claude-import")
            .hasMessageContaining("app.message.import")
            .hasMessageContaining("app.key.type=long")
            .hasMessageContaining("[uuid]")
            .hasMessageContaining("a bigint cannot hold a UUID")

        assertThat(Sentinel.built.get()).isZero()
    }

    open class Sentinel {
        init {
            built.incrementAndGet()
        }

        companion object {
            val built = AtomicInteger()
        }
    }
}
