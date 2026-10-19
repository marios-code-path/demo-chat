package com.demo.chat.shell.cli

import com.demo.chat.shell.commands.LoginCommands
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.io.OutputStreamWriter
import java.io.PrintWriter

/**
 * The modes of the shell. `chat.shell.mode` is `interactive` (the default),
 * `one-shot`, or `resident`.
 *
 * **A mode other than `interactive` replaces the application runner of Spring
 * Shell.** The auto-configuration of Spring Shell declares its runner with
 * `@ConditionalOnMissingBean(name = "springShellApplicationRunner")`, so a
 * bean of that name wins. Without the property, nothing here changes.
 */
@Configuration(proxyBeanMethods = false)
@Profile("shell")
class ShellModeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun environmentLogin(login: LoginCommands<*>): EnvironmentLogin =
        EnvironmentLogin(System::getenv) { user, password -> login.login(user, password) }

    @Bean("springShellApplicationRunner")
    @ConditionalOnProperty(prefix = "chat.shell", name = ["mode"], havingValue = "one-shot")
    fun oneShotRunner(execution: CommandLineExecution, login: EnvironmentLogin): ApplicationRunner =
        OneShotRunner(execution, login)
}

/**
 * Runs the program arguments as one command line, and ends.
 *
 * It logs in from the environment first. It then runs the command with the
 * process standard input as the command input. A status other than OK becomes
 * a [ShellExit], and the entry point turns that into the process exit status.
 *
 * **The arguments join with one space, as Spring Shell joins them.** The
 * quotes that a caller typed are gone by then, so the entry text of `post`
 * travels on standard input and not as an argument.
 *
 * Every [ShellExit] that leaves this runner has its line on standard error
 * already. [CommandLineExecution] writes the line of a failed command. This
 * class writes the line of a failed login.
 */
class OneShotRunner(private val execution: CommandLineExecution, private val login: EnvironmentLogin) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        // The writers encode as UTF-8 and pass the bytes to the stream, so a terminal locale cannot change the text.
        val out = PrintWriter(OutputStreamWriter(System.out, Charsets.UTF_8))
        val err = PrintWriter(OutputStreamWriter(System.err, Charsets.UTF_8))
        try {
            login.loginIfSet()
        } catch (e: ShellExit) {
            err.println("chat-shell: ${e.message}")
            err.flush()
            throw e
        }
        val status = execution.run(args.sourceArgs.joinToString(" "), System.`in`, out, err)
        if (status != ShellExitStatus.OK) throw ShellExit(status, "the command ended with status ${status.code}")
    }
}
