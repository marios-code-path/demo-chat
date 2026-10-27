package com.demo.chat.test.portal

import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.shell.cli.CommandLineExecution
import com.demo.chat.shell.cli.EnvironmentLogin
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import com.demo.chat.shell.cli.ShellModeConfiguration
import com.demo.chat.shell.cli.StreamInput
import com.demo.chat.shell.portal.PortalCommandRegistrar
import com.demo.chat.shell.portal.PortalRunner
import com.demo.chat.shell.portal.PortalServerFactory
import com.demo.chat.shell.portal.PortalSocketServer
import com.demo.chat.shell.portal.ShutdownHooks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandExecutor
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import java.io.ByteArrayInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.function.Function

/**
 * The portal launch paths. Portal mode logs in before it binds the socket.
 * The interactive command checks the shell login before it binds. One-shot
 * mode refuses the portal command. See CHAT-hmbigvmf.
 */
class PortalModeTests {
    @TempDir
    lateinit var dir: Path

    private val events = mutableListOf<String>()
    private var created: Path? = null
    private var bindCalls = 0
    private var serveCalls = 0
    private var closeCalls = 0

    private val server: PortalSocketServer = mock(PortalSocketServer::class.java)

    init {
        doAnswer { bindCalls++; events += "bind" }.`when`(server).bind()
        doAnswer { serveCalls++; events += "serve" }.`when`(server).serve()
        doAnswer { closeCalls++; events += "close" }.`when`(server).close()
    }

    private val factory = PortalServerFactory { path ->
        created = path
        events += "create"
        server
    }

    private class Hooks : ShutdownHooks {
        val added = mutableListOf<Thread>()
        val removed = mutableListOf<Thread>()

        override fun add(hook: Thread) {
            added += hook
        }

        override fun remove(hook: Thread) {
            removed += hook
        }
    }

    private val hooks = Hooks()

    @AfterEach
    fun clearLogin() {
        ShellStateConfiguration.clearLogin()
    }

    private fun passwordFile(): String =
        dir.resolve("password").also { Files.writeString(it, "pw\n") }.toString()

    private fun login(refuse: Boolean = false): EnvironmentLogin {
        val env = mapOf(
            EnvironmentLogin.USER to "Admin",
            EnvironmentLogin.PASSWORD_FILE to passwordFile(),
        )
        return EnvironmentLogin({ env[it] }) { user, _ ->
            events += "login $user"
            if (refuse) throw IllegalStateException("denied")
        }
    }

    private fun socket(): Path = dir.resolve("portal.sock")

    private fun runner(login: EnvironmentLogin = login(), socket: String = socket().toString()): PortalRunner =
        PortalRunner(login, factory, socket, hooks)

    private fun failure(block: () -> Unit): ShellExit {
        val error = try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
        assertThat(error).isInstanceOf(ShellExit::class.java)
        return error as ShellExit
    }

    private fun executePortal(path: Path): String {
        val command = PortalCommandRegistrar(factory, hooks).portalCommand()
        val registry = CommandRegistry(setOf(command))
        val parsed = DefaultCommandParser(registry).parse("portal --socket $path")
        val out = StringWriter()
        CommandExecutor(registry).execute(
            CommandContext(parsed, registry, PrintWriter(out), StreamInput(ByteArrayInputStream(ByteArray(0)))),
        )
        return out.toString()
    }

    @Test
    fun `portal mode refuses a missing login before bind`() {
        val missing = EnvironmentLogin({ null }, { _, _ -> })

        val failure = failure { PortalRunner(missing, factory, socket().toString(), hooks).run(DefaultApplicationArguments()) }

        assertThat(failure.status).isEqualTo(ShellExitStatus.LOGIN)
        assertThat(created).isNull()
        verifyNoInteractions(server)
    }

    @Test
    fun `portal mode refuses a refused login before bind`() {
        val failure = failure { runner(login(refuse = true)).run(DefaultApplicationArguments()) }

        assertThat(failure.status).isEqualTo(ShellExitStatus.LOGIN)
        assertThat(created).isNull()
        verifyNoInteractions(server)
    }

    @Test
    fun `portal mode serves after a valid login`() {
        runner().run(DefaultApplicationArguments())

        assertThat(events).containsExactly("login Admin", "create", "bind", "serve", "close")
        assertThat(created).isEqualTo(socket())
    }

    @Test
    fun `portal mode registers one shutdown hook and closes the server when it ends`() {
        runner().run(DefaultApplicationArguments())

        assertThat(hooks.added).hasSize(1)
        assertThat(hooks.removed).hasSize(1)
        assertThat(closeCalls).isEqualTo(1)

        hooks.added.single().run()

        assertThat(closeCalls).isEqualTo(2)
    }

    @Test
    fun `portal mode refuses a blank socket path before create`() {
        val failure = failure { runner(socket = "").run(DefaultApplicationArguments()) }

        assertThat(failure.status).isEqualTo(ShellExitStatus.USAGE)
        assertThat(created).isNull()
        verifyNoInteractions(server)
    }

    @Test
    fun `portal mode defines the application runner after a valid login`() {
        ApplicationContextRunner()
            .withPropertyValues(
                "spring.profiles.active=shell",
                "chat.shell.mode=portal",
                "chat.shell.portal.socket=${socket()}",
            )
            .withUserConfiguration(ShellModeConfiguration::class.java)
            .withBean(PortalServerFactory::class.java, { factory })
            .withBean(ShutdownHooks::class.java, { hooks })
            .withBean(EnvironmentLogin::class.java, { login() })
            .run { context ->
                assertThat(context).hasBean("springShellApplicationRunner")
                context.getBean("springShellApplicationRunner", org.springframework.boot.ApplicationRunner::class.java)
                    .run(DefaultApplicationArguments())
            }

        assertThat(events).containsExactly("login Admin", "create", "bind", "serve", "close")
    }

    @Test
    fun `the portal command is registered`() {
        ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=shell")
            .withUserConfiguration(PortalCommandRegistrar::class.java)
            .withBean(PortalServerFactory::class.java, { factory })
            .withBean(ShutdownHooks::class.java, { hooks })
            .run { context ->
                assertThat(context).hasBean("portalCommand")
                assertThat(context.getBean("portalCommand", Command::class.java).name).isEqualTo("portal")
            }
    }

    @Test
    fun `the portal command refuses without a login and does not bind`() {
        ShellStateConfiguration.clearLogin()

        val error = try {
            executePortal(socket())
            null
        } catch (e: Throwable) {
            e
        }

        assertThat(ShellExitStatus.of(error!!)).isEqualTo(ShellExitStatus.LOGIN)
        verifyNoInteractions(server)
    }

    @Test
    fun `the portal command serves after a valid login`() {
        ShellStateConfiguration.loggedInUser = Optional.of<Any>("Admin")
        ShellStateConfiguration.loginMetadata = Optional.of(UsernamePasswordMetadata("Admin", "pw"))

        val output = executePortal(socket())

        assertThat(bindCalls).isEqualTo(1)
        assertThat(serveCalls).isEqualTo(1)
        assertThat(output).contains("portal closed")
    }

    @Test
    fun `one-shot mode refuses the portal command`() {
        val command = Command.builder()
            .name("portal")
            .group("Test")
            .execute(Function<CommandContext, String> { "served" })
        val registry = CommandRegistry(setOf(command))
        val execution = CommandLineExecution(DefaultCommandParser(registry), registry)
        val out = StringWriter()
        val err = StringWriter()

        val status = execution.run(
            "portal --socket ${socket()}",
            ByteArrayInputStream(ByteArray(0)),
            PrintWriter(out),
            PrintWriter(err),
        )

        assertThat(status).isEqualTo(ShellExitStatus.USAGE)
        assertThat(err.toString()).contains("portal is not allowed in this mode")
    }
}
