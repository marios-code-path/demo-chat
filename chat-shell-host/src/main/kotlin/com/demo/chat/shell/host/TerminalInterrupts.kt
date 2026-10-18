package com.demo.chat.shell.host

import org.jline.terminal.Terminal

/**
 * Sends Ctrl-C on the shell terminal to the running `!` command.
 *
 * **Without this, Ctrl-C ends the shell.** JLine handles INT only while it
 * reads a line. While a command runs, the terminal sends SIGINT to the whole
 * foreground process group, and the JVM keeps its default handler, so it
 * exits. A pty probe on 2026-10-17 measured this: one Ctrl-C ended the JVM
 * with SIGINT.
 *
 * [invoke] sets an INT handler that runs the action. The returned handle puts
 * the earlier handler back, so INT at the prompt behaves as before. See
 * CHAT-wdokzwnj.
 */
class TerminalInterrupts(private val terminal: Terminal) : (Runnable) -> AutoCloseable {

    override fun invoke(action: Runnable): AutoCloseable {
        val earlier = terminal.handle(Terminal.Signal.INT) { action.run() }
        return AutoCloseable { terminal.handle(Terminal.Signal.INT, earlier) }
    }
}
