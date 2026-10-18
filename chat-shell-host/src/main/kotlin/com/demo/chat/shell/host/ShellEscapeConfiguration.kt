package com.demo.chat.shell.host

import org.jline.reader.LineReader
import org.jline.terminal.Terminal
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.CommandParser
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import org.springframework.shell.core.command.Script

/**
 * The wiring of the `!` escape.
 *
 * **The parser bean reaches every runner.** `ShellRunnerAutoConfiguration`
 * declares its `CommandParser` with `@ConditionalOnMissingBean`, and the
 * interactive and non-interactive runners take that bean. So this bean
 * replaces it. `script -f` builds its own parser, so [ShellEscapePostProcessor]
 * sets this one on the `Script` bean.
 *
 * The post-processor is a static bean, so it does not start this
 * configuration early. It reads the parser through a provider for the same
 * reason. See CHAT-jrlctimq.
 */
@Configuration
@Profile("shell")
class ShellEscapeConfiguration {

    @Bean
    fun bangCommandParser(registry: CommandRegistry): CommandParser =
        BangCommandParser(DefaultCommandParser(registry))

    /** Ctrl-C on the shell terminal stops a running `!` command. See CHAT-wdokzwnj. */
    @Bean
    fun hostCommand(terminal: ObjectProvider<Terminal>): HostCommand =
        terminal.ifAvailable?.let { HostCommand(interrupts = TerminalInterrupts(it)) } ?: HostCommand()

    companion object {
        @Bean
        @JvmStatic
        fun shellEscapePostProcessor(parser: ObjectProvider<CommandParser>): BeanPostProcessor =
            ShellEscapePostProcessor(parser)
    }
}

/**
 * Turns JLine history expansion off, and gives `script -f` the `!` parser.
 *
 * **History expansion corrupts a `!` line.** JLine reads `!jq` as a history
 * event and fails with "event not found", or puts an earlier command in its
 * place. It also removes backslashes, so `"\(.uuid)\t"` became `"(.uuid)t"`.
 * `DISABLE_EVENT_EXPANSION` returns the line byte for byte. `!!` and `!n` stop
 * working. The arrow keys and the `history` command still reach the history.
 *
 * The `lineReader` bean is not `@ConditionalOnMissingBean`, so a replacement
 * bean would collide. This changes one option and leaves the rest of the bean.
 */
class ShellEscapePostProcessor(private val parser: ObjectProvider<CommandParser>) : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        when (bean) {
            is LineReader -> bean.option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
            is Script -> bean.setCommandParser(parser.getObject())
        }
        return bean
    }
}
