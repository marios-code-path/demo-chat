package com.demo.chat.shell.host

import org.springframework.shell.core.command.CommandArgument
import org.springframework.shell.core.command.CommandParser
import org.springframework.shell.core.command.ParsedInput

/**
 * Sends a line that starts with `!` to the `!` command as one argument.
 *
 * **The default parser cannot carry a shell line.** `DefaultCommandParser`
 * splits on blanks, keeps only a double-quoted string as one word, and reads
 * a word that starts with `-` as an option. `ParsedInput` keeps no raw line.
 * So this parser takes a `!` line before the default parser sees it.
 *
 * The argument is the rest of the line after the `!`, with the leading blanks
 * removed and no other change. Every other line goes to [delegate] unchanged.
 *
 * **An interactive line arrives byte for byte. A script line does not.**
 * Spring Shell changes a `script -f` line before any parser receives it. See
 * docs/SHELL-COMMAND-CONTRACT.md and CHAT-jrlctimq.
 */
class BangCommandParser(private val delegate: CommandParser) : CommandParser {

    override fun parse(input: String): ParsedInput {
        val line = input.trimStart()
        if (!line.startsWith(BANG)) {
            return delegate.parse(input)
        }
        val rest = line.substring(BANG.length).trimStart()
        val parsed = ParsedInput.builder().commandName(BANG)
        if (rest.isNotEmpty()) {
            parsed.addArgument(CommandArgument.with().index(0).value(rest).type(String::class.java).build())
        }
        return parsed.build()
    }

    companion object {
        const val BANG = "!"
    }
}
