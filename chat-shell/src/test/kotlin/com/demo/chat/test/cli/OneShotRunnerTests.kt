package com.demo.chat.test.cli

import com.demo.chat.shell.cli.CommandLineExecution
import com.demo.chat.shell.cli.EnvironmentLogin
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import com.demo.chat.shell.cli.ShellModeConfiguration
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * One-shot mode replaces the application runner of Spring Shell. The runner
 * logs in from the environment, joins the program arguments into one command
 * line, and turns a status other than OK into a [ShellExit]. See CHAT-jmnjvupr.
 */
class OneShotRunnerTests {
    @TempDir
    lateinit var dir: Path

    private val events = mutableListOf<String>()
    private val execution = mock(CommandLineExecution::class.java)
    private var written = ""

    /** A login with both variables set. It records its call, and it can fail. */
    private fun login(fails: Boolean = false): EnvironmentLogin {
        val file = dir.resolve("password").also { Files.writeString(it, "pw\n") }.toString()
        val env = mapOf(EnvironmentLogin.USER to "Admin", EnvironmentLogin.PASSWORD_FILE to file)
        return EnvironmentLogin({ env[it] }) { user, _ ->
            events += "login $user"
            if (fails) throw IllegalStateException("denied")
        }
    }

    private fun contexts(mode: String?, loginFails: Boolean = false): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*listOfNotNull("spring.profiles.active=shell", mode?.let { "chat.shell.mode=$it" }).toTypedArray())
            .withUserConfiguration(ShellModeConfiguration::class.java)
            .withBean(CommandLineExecution::class.java, { execution })
            .withBean(EnvironmentLogin::class.java, { login(loginFails) })

    private fun answer(status: ShellExitStatus) {
        given(execution.run(anyString(), anyObject(), anyObject(), anyObject())).willAnswer {
            events += "run ${it.getArgument<String>(0)}"
            status
        }
    }

    /** Runs the one-shot runner. [written] holds what it wrote to standard error, even when it throws. */
    private fun run(vararg args: String, loginFails: Boolean = false) {
        val original = System.err
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured, true, Charsets.UTF_8))
        try {
            contexts("one-shot", loginFails).run { context ->
                context.getBean("springShellApplicationRunner", ApplicationRunner::class.java)
                    .run(DefaultApplicationArguments(*args))
            }
        } finally {
            System.setErr(original)
            written = captured.toString(Charsets.UTF_8)
        }
    }

    @Test
    fun `one-shot mode defines springShellApplicationRunner, and interactive mode does not`() {
        contexts("one-shot").run { assertThat(it).hasBean("springShellApplicationRunner") }
        contexts("interactive").run { assertThat(it).doesNotHaveBean("springShellApplicationRunner") }
        contexts(null).run { assertThat(it).doesNotHaveBean("springShellApplicationRunner") }
    }

    @Test
    fun `the runner logs in before the command`() {
        answer(ShellExitStatus.OK)

        run("whoami")

        assertThat(events).containsExactly("login Admin", "run whoami")
    }

    @Test
    fun `the runner joins the arguments into one line`() {
        answer(ShellExitStatus.OK)

        run("messages", "lobby", "--limit", "1")

        assertThat(events).containsExactly("login Admin", "run messages lobby --limit 1")
    }

    @Test
    fun `a status other than OK becomes a ShellExit with that status`() {
        answer(ShellExitStatus.USAGE)

        val failure = org.junit.jupiter.api.assertThrows<Throwable> { run("no-such-command") }

        assertThat(failure).isInstanceOf(ShellExit::class.java)
        assertThat((failure as ShellExit).status).isEqualTo(ShellExitStatus.USAGE)
    }

    @Test
    fun `a failed login writes one line, runs no command, and becomes a ShellExit`() {
        answer(ShellExitStatus.OK)

        val failure = org.junit.jupiter.api.assertThrows<Throwable> { run("whoami", loginFails = true) }

        assertThat(failure).isInstanceOf(ShellExit::class.java)
        assertThat((failure as ShellExit).status).isEqualTo(ShellExitStatus.LOGIN)
        assertThat(events).containsExactly("login Admin")
        assertThat(written).isEqualTo("chat-shell: Authentication failed: denied" + System.lineSeparator())
    }
}
