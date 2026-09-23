package com.demo.chat.test.deploy.init

import com.demo.chat.config.deploy.init.UserInitializationProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.FileSystemResource
import java.io.File

/**
 * The shipped user initialization file parses and binds.
 *
 * `shared-deploy-configuration/src/main/config/userinit.yml` is loaded by
 * every deployment. **Nothing checked it before 2026-09-23.** A draft policy
 * written into that file made it invalid YAML, and no build reported it,
 * because no module reads the file at test time.
 *
 * These tests read the shipped file from disk, because
 * `shared-deploy-configuration` reaches `chat-deploy` only under the
 * `expose-webflux` profile.
 *
 * See `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`.
 */
class UserInitConfigBindingTests {

    @Test
    fun `the shipped file parses and binds to its properties type`() {
        val properties = binder().bind("app.init", UserInitializationProperties::class.java)

        assertThat(properties.isBound)
            .withFailMessage("app.init did not bind to UserInitializationProperties")
            .isTrue()

        val bound = properties.get()
        assertThat(bound.passwordEncoder).isEqualTo("bcrypt")
        assertThat(bound.initialUsers.keys).contains("Anon", "Admin")
        assertThat(bound.initialRoles.roles).isNotEmpty()
    }

    /**
     * **This is the guard that the draft policy needed.**
     *
     * Spring ignores a key that no constructor parameter names, so an
     * unbound section reaches production and has no effect and no error.
     * `UserInitializationProperties` names three.
     */
    @Test
    fun `the shipped file declares no key that nothing binds`() {
        // The property names arrive in relaxed form, so both sides are lower case.
        val bound = setOf("passwordencoder", "initialroles", "initialusers")

        val declared = propertyNames()
            .filter { it.startsWith("$PREFIX.") }
            .map { it.removePrefix("$PREFIX.").substringBefore('.').substringBefore('[') }
            .toSet()

        assertThat(declared)
            .withFailMessage(
                "app.init declares %s, and UserInitializationProperties binds %s. " +
                    "An unbound key has no effect at runtime.", declared, bound
            )
            .isSubsetOf(bound)
    }

    /** Every role names the three fields that `RoleDefinition` binds. */
    @Test
    fun `every role declares only the fields that RoleDefinition binds`() {
        val allowed = setOf("user", "target", "role")

        val fields = propertyNames()
            .filter { it.startsWith("$PREFIX.initialroles.roles[") }
            .map { it.substringAfterLast('.') }
            .toSet()

        assertThat(fields).isSubsetOf(allowed)
    }

    private fun binder(): Binder = Binder(ConfigurationPropertySources.from(propertySources()))

    private fun propertyNames(): List<String> = propertySources()
        .filterIsInstance<EnumerablePropertySource<*>>()
        .flatMap { it.propertyNames.toList() }
        .map { it.lowercase() }

    private fun propertySources(): MutablePropertySources {
        val sources = MutablePropertySources()
        YamlPropertySourceLoader()
            .load("userinit", FileSystemResource(shippedFile()))
            .forEach(sources::addLast)
        return sources
    }

    private fun shippedFile(): File {
        val file = File(System.getProperty("user.dir")).resolveSibling(SHIPPED_PATH)

        check(file.isFile) { "The shipped configuration is not at ${file.absolutePath}" }

        return file
    }

    private companion object {
        const val PREFIX = "app.init"
        const val SHIPPED_PATH = "shared-deploy-configuration/src/main/config/userinit.yml"
    }
}
