package com.demo.chat.test.host

import com.demo.chat.shell.host.TerminalInterrupts
import org.assertj.core.api.Assertions.assertThat
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Ctrl-C on the shell terminal reaches the running `!` command, and the
 * earlier handler returns after the command. `Terminal.raise` calls the
 * handler that the terminal holds, as a real SIGINT does. See CHAT-wdokzwnj.
 */
class TerminalInterruptsTests {

    private val terminal: Terminal = TerminalBuilder.builder()
        .dumb(true)
        .streams(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream())
        .build()

    @Test
    fun `INT runs the action while the command holds it`() {
        var ran = false

        TerminalInterrupts(terminal)(Runnable { ran = true }).use {
            terminal.raise(Terminal.Signal.INT)
        }

        assertThat(ran).isTrue()
    }

    @Test
    fun `the earlier handler returns when the command ends`() {
        var earlier = 0
        var action = 0
        terminal.handle(Terminal.Signal.INT) { earlier++ }

        TerminalInterrupts(terminal)(Runnable { action++ }).close()
        terminal.raise(Terminal.Signal.INT)

        assertThat(earlier).isEqualTo(1)
        assertThat(action).isEqualTo(0)
    }
}
