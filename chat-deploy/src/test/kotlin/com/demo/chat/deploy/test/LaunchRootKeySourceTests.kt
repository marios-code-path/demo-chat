package com.demo.chat.deploy.test

import com.demo.chat.config.deploy.init.RootKeySource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.FileSystemResource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Each launch that `chat-build` generates must pass the root key source check.
 * See `CHAT-avduuqwp`.
 *
 * The test reads the golden flag files of `shell-scripts/test-flags.sh`. That
 * script proves that `chat-build` emits them. So the two checks together tie a
 * generated launch to the startup rule. For the authorization server, the test
 * also loads the `application.yml` of its module. The launch flags take
 * precedence, as system properties do.
 */
class LaunchRootKeySourceTests {

    private val golden: Path = Path.of("..", "shell-scripts", "golden")

    private val expected = mapOf(
        "core-" to RootKeySource.STORE,
        "authserv-" to RootKeySource.NONE,
        "shell-consul" to RootKeySource.KV,
        "shell-client" to RootKeySource.HTTP,
        "rest-client" to RootKeySource.HTTP,
        "gateway-client" to RootKeySource.HTTP,
    )

    @Test
    fun `every generated launch passes the root key source check with its expected source`() {
        val files = Files.list(golden).use { s -> s.filter { it.toString().endsWith(".flags") }.sorted().toList() }
        assertThat(files).hasSizeGreaterThanOrEqualTo(17)

        val results = files.associate { file ->
            val name = file.fileName.toString().removeSuffix(".flags")
            val source = try {
                RootKeySource.of(environment(name, file))
            } catch (e: Exception) {
                fail<RootKeySource>("The launch $name fails the root key source check: ${e.message}")
            }
            name to source
        }

        results.forEach { (name, source) ->
            val want = expected.entries.firstOrNull { name.startsWith(it.key) }?.value
                ?: fail("The launch $name has no expected root key source in this test.")
            assertThat(source).`as`(name).isEqualTo(want)
        }
    }

    @Test
    fun `the authorization server configuration refuses a consume scheme`() {
        val env = StandardEnvironment()
        env.propertySources.addFirst(MapPropertySource("flags", mapOf("app.rootkeys.consume.scheme" to "http")))
        authServerYaml().forEach { env.propertySources.addLast(it) }

        assertThat(runCatching { RootKeySource.of(env) }.exceptionOrNull())
            .hasMessageContaining("app.rootkeys.required=false")
    }

    private fun environment(name: String, file: Path): StandardEnvironment {
        val env = StandardEnvironment()
        env.propertySources.addFirst(MapPropertySource("flags", flags(file)))
        if (name.startsWith("authserv-")) authServerYaml().forEach { env.propertySources.addLast(it) }
        return env
    }

    /** A `-Dname` flag with no value sets the empty string, as the JVM does. */
    private fun flags(file: Path): Map<String, Any> = Files.readAllLines(file)
        .filter { it.startsWith("-D") }
        .associate { line ->
            val body = line.removePrefix("-D")
            val cut = body.indexOf('=')
            if (cut < 0) body to "" else body.substring(0, cut) to body.substring(cut + 1)
        }

    private fun authServerYaml() = YamlPropertySourceLoader().load(
        "authserv", FileSystemResource(Path.of("..", "chat-authorization-server", "src", "main", "resources", "application.yml"))
    )
}
