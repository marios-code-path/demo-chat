package com.demo.chat.test.host

import com.demo.chat.shell.host.BangCommandParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandOption
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import java.util.function.Consumer

/**
 * A line that starts with `!` reaches the `!` command as one argument.
 *
 * **The default parser cannot carry a shell line.** It splits on blanks, it
 * keeps only a double-quoted string as one word, and it reads a word that
 * starts with `-` as an option. So these cases fail if the wrapper hands a `!`
 * line to the default parser. See CHAT-jrlctimq.
 */
class BangCommandParserTests {

    private val registry = CommandRegistry(
        setOf(
            Command.builder().name("!").execute(Consumer { }),
            Command.builder().name("join")
                .options(CommandOption.with().longName("topic").type(String::class.java).build())
                .execute(Consumer { }),
        )
    )
    private val parser = BangCommandParser(DefaultCommandParser(registry))

    private fun argumentsOf(line: String): List<String?> =
        parser.parse(line).arguments().map { it.value() }

    @Test
    fun `a bang line keeps quotes pipes backslashes dollars and options`() {
        val line = """jq -r '.[] | "\(.uuid)\t${'$'}HOME"' --raw-output ../conversations.json"""

        val parsed = parser.parse("!$line")

        assertThat(parsed.commandName()).isEqualTo("!")
        assertThat(parsed.options()).isEmpty()
        assertThat(argumentsOf("!$line")).containsExactly(line)
    }

    @Test
    fun `blanks before and after the bang are removed and inner blanks stay`() {
        assertThat(argumentsOf("   !   printf '%s' 'a  b'")).containsExactly("printf '%s' 'a  b'")
    }

    @Test
    fun `a bang alone gives the bang command with no argument`() {
        val parsed = parser.parse("  !  ")

        assertThat(parsed.commandName()).isEqualTo("!")
        assertThat(parsed.arguments()).isEmpty()
    }

    @Test
    fun `any other line parses as the default parser parses it`() {
        val line = "join --topic lobby"

        assertThat(parser.parse(line)).isEqualTo(DefaultCommandParser(registry).parse(line))
    }
}
