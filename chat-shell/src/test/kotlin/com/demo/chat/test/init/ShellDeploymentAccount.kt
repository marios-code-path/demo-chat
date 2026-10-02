package com.demo.chat.test.init

import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource

/**
 * The shipped Admin account, read from the config the deployment loads.
 *
 * **The test states no credential.** It reads the value declared by the
 * shipped `config/userinit.yml`, which is the same file the container image
 * loads from its own classpath. One source holds the password, so a change to
 * the config reaches the test and no secret enters the test source.
 *
 * `InitialUsersService` generates a password when an entry carries none, and
 * a test cannot read a generated value. This reader refuses a blank or absent
 * password, and its message names the key to set. See `CHAT-wbcbptiq`.
 */
object ShellDeploymentAccount {

    /** The handle of the shipped administrator. `userinit.yml` declares it. */
    const val ADMIN_HANDLE = "Admin"

    private const val CONFIG_PATH = "config/userinit.yml"
    private const val ADMIN_PASSWORD_SUFFIX = ".Admin.password"

    val adminPassword: String by lazy { readAdminPassword() }

    private fun readAdminPassword(): String {
        val sources = YamlPropertySourceLoader().load(CONFIG_PATH, ClassPathResource(CONFIG_PATH))

        val key = sources
            .filterIsInstance<EnumerablePropertySource<*>>()
            .flatMap { source -> source.propertyNames.toList() }
            .firstOrNull { it.startsWith("app.init.") && it.endsWith(ADMIN_PASSWORD_SUFFIX) }

        val value = key?.let { name -> sources.firstNotNullOfOrNull { it.getProperty(name) as String? } }

        require(!value.isNullOrBlank()) {
            "classpath:$CONFIG_PATH declares no password for $ADMIN_HANDLE. " +
                "A blank password makes the start generate one, and a test cannot read a generated value. " +
                "Declare a password for $ADMIN_HANDLE in that file."
        }

        return value
    }
}
