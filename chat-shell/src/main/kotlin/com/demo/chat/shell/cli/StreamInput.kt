package com.demo.chat.shell.cli

import org.springframework.shell.core.InputReader
import java.io.InputStream

/**
 * The command input of the shell modes.
 *
 * An entry travels as input, not as an argument, so its newlines need no
 * quotes. [readAll] decodes the bytes as UTF-8 and changes nothing. A final
 * newline, a `\r\n` line end, and an empty input all stay as sent.
 */
class StreamInput(private val input: InputStream) : InputReader {
    fun readAll(): String = String(input.readAllBytes(), Charsets.UTF_8)
}
