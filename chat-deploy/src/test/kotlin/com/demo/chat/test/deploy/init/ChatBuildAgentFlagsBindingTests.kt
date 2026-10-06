package com.demo.chat.test.deploy.init

import com.demo.chat.config.deploy.init.UserInitializationProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.FileSystemResource
import java.io.File

/**
 * The agent user flags that `chat-build --agent` emits bind with the shipped
 * file. See `CHAT-frcrctdp`.
 *
 * This test reads `shell-scripts/golden/core-agent-brackets.flags`.
 * `test-flags.sh` holds that file equal to the emitter output. So a change to
 * the emitter fails `test-flags.sh` first, and this test after the golden is
 * written again.
 */
class ChatBuildAgentFlagsBindingTests {

    @Test
    fun `the emitted Bot_1 and Bot1 flags bind to two users with their own handles`() {
        val sources = MutablePropertySources().apply {
            addLast(MapPropertySource("launch", emittedUserFlags()))
            YamlPropertySourceLoader().load("userinit", FileSystemResource(repo("shared-deploy-configuration/src/main/config/userinit.yml")))
                .forEach(::addLast)
        }

        val bound = Binder(ConfigurationPropertySources.from(sources))
            .bind("app.init", UserInitializationProperties::class.java).get()

        assertThat(bound.initialUsers.getValue("Bot_1").handle).isEqualTo("Bot_1")
        assertThat(bound.initialUsers.getValue("Bot1").handle).isEqualTo("Bot1")
        assertThat(bound.initialUsers.keys).contains("Admin", "Anon", "Agent", "Service")
    }

    private fun emittedUserFlags(): Map<String, Any> =
        repo("shell-scripts/golden/core-agent-brackets.flags").readLines()
            .filter { it.startsWith("-Dapp.init.initial-users") }
            .associate { line -> line.removePrefix("-D").substringBefore("=") to line.substringAfter("=") }
            .also { assertThat(it).describedAs("the emitted agent user flags").hasSize(6) }

    private fun repo(path: String): File =
        File(System.getProperty("user.dir")).resolveSibling(path).also { check(it.isFile) { "No file at $it" } }
}
