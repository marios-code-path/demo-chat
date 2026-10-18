package com.demo.chat.test.cli

import com.demo.chat.shell.cli.EnvironmentLogin
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.ConnectException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The login of the shell modes reads `CHAT_SHELL_USER` and
 * `CHAT_SHELL_PASSWORD_FILE`. A password never goes in a program argument,
 * because `ps` shows arguments. See CHAT-jmnjvupr.
 */
class EnvironmentLoginTests {
    @TempDir
    lateinit var dir: Path

    private val calls = mutableListOf<Pair<String, String>>()

    private fun login(env: Map<String, String>, refuse: Throwable? = null) =
        EnvironmentLogin({ env[it] }, { user, password ->
            calls += user to password
            if (refuse != null) throw refuse
        })

    private fun passwordFile(text: String): String =
        dir.resolve("password").also { Files.write(it, text.toByteArray()) }.toString()

    private fun failure(block: () -> Unit): ShellExit {
        val error = catchThrowable(block)
        assertThat(error).isInstanceOf(ShellExit::class.java)
        return error as ShellExit
    }

    private fun catchThrowable(block: () -> Unit): Throwable? = try { block(); null } catch (e: Throwable) { e }

    @Test
    fun `no variables means no login`() {
        login(emptyMap()).loginIfSet()
        login(mapOf("CHAT_SHELL_USER" to "", "CHAT_SHELL_PASSWORD_FILE" to "")).loginIfSet()

        assertThat(calls).isEmpty()
    }

    @Test
    fun `one variable alone is refused with USAGE`() {
        for (env in listOf(
            mapOf("CHAT_SHELL_USER" to "Admin"),
            mapOf("CHAT_SHELL_PASSWORD_FILE" to passwordFile("pw")),
        )) {
            val exit = failure { login(env).loginIfSet() }

            assertThat(exit.status).isEqualTo(ShellExitStatus.USAGE)
            assertThat(exit.message).isEqualTo("set both CHAT_SHELL_USER and CHAT_SHELL_PASSWORD_FILE")
        }
        assertThat(calls).isEmpty()
    }

    @Test
    fun `the password file loses one trailing newline and nothing more`() {
        for ((text, want) in listOf("pw\n" to "pw", "pw\r\n" to "pw", "pw\n\n" to "pw\n", " pw " to " pw ", "pw" to "pw", "pw\r" to "pw\r")) {
            calls.clear()
            login(mapOf("CHAT_SHELL_USER" to "Admin", "CHAT_SHELL_PASSWORD_FILE" to passwordFile(text))).loginIfSet()

            assertThat(calls).describedAs(text).containsExactly("Admin" to want)
        }
    }

    @Test
    fun `a missing or empty password file gives LOGIN, and the message has no password`() {
        val missing = dir.resolve("absent").toString()
        val empty = passwordFile("\n")

        for (file in listOf(missing, empty)) {
            val exit = failure { login(mapOf("CHAT_SHELL_USER" to "Admin", "CHAT_SHELL_PASSWORD_FILE" to file)).loginIfSet() }

            assertThat(exit.status).isEqualTo(ShellExitStatus.LOGIN)
            assertThat(exit.message).contains(file)
        }
        assertThat(calls).isEmpty()
    }

    @Test
    fun `a refused login gives LOGIN`() {
        val env = mapOf("CHAT_SHELL_USER" to "Admin", "CHAT_SHELL_PASSWORD_FILE" to passwordFile("topsecret"))

        val exit = failure {
            login(env, IllegalStateException("Authentication failed: denied", RuntimeException("denied"))).loginIfSet()
        }

        assertThat(exit.status).isEqualTo(ShellExitStatus.LOGIN)
        assertThat(exit.message).isEqualTo("Authentication failed: denied").doesNotContain("topsecret")
    }

    @Test
    fun `a core that is down gives UNAVAILABLE and not LOGIN`() {
        val env = mapOf("CHAT_SHELL_USER" to "Admin", "CHAT_SHELL_PASSWORD_FILE" to passwordFile("pw"))

        val exit = failure {
            login(env, IllegalStateException("Authentication failed: down", ConnectException("Connection refused"))).loginIfSet()
        }

        assertThat(exit.status).isEqualTo(ShellExitStatus.UNAVAILABLE)
        assertThat(exit.message).isEqualTo("Authentication failed: Connection refused")
    }
}
