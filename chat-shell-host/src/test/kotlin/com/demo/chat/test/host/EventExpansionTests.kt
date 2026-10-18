package com.demo.chat.test.host

import com.demo.chat.shell.host.ShellEscapePostProcessor
import org.assertj.core.api.Assertions.assertThat
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.shell.core.command.CommandParser
import org.springframework.shell.jline.ExtendedDefaultParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * JLine history expansion must not touch a `!` line.
 *
 * The probe of 2026-10-16, as a test. A line reader with the Spring Shell
 * parser expands `!` events and removes backslashes. The jq line below came
 * back as `'.[] | "(.uuid)t(.name)"'`. See CHAT-jrlctimq.
 */
class EventExpansionTests {

    private val jqLine = """!jq -r '.[] | "\(.uuid)\t\(.name)"' ../conversations.json"""

    private fun readerFor(input: String): LineReader {
        val terminal = TerminalBuilder.builder()
            .dumb(true)
            .streams(ByteArrayInputStream("$input\n".toByteArray()), ByteArrayOutputStream())
            .build()
        return LineReaderBuilder.builder().terminal(terminal).parser(ExtendedDefaultParser()).build()
    }

    @Test
    fun `the post-processed reader returns a bang line byte for byte`() {
        val reader = ShellEscapePostProcessor(StaticListableBeanFactory().getBeanProvider(CommandParser::class.java))
            .postProcessAfterInitialization(readerFor(jqLine), "lineReader") as LineReader

        assertThat(reader.readLine()).isEqualTo(jqLine)
    }

    @Test
    fun `the default reader changes the same line`() {
        // This case proves that the line above exercises expansion. If JLine
        // stopped expanding events, both cases would pass for no reason.
        val returned = runCatching { readerFor(jqLine).readLine() }

        assertThat(returned.getOrNull()).isNotEqualTo(jqLine)
    }
}
