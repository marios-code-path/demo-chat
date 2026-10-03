package com.demo.chat.shell.commands

import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption

/**
 * The option reader that every migrated command uses.
 *
 * **Spring Shell 4 does not bind options to method parameters.** The
 * declarative model did that, and it is gone. A programmatic command reads
 * each option from the context by its long name, so this is the one place
 * that knows how a value and a default combine.
 *
 * **The parsed input holds only the options that the caller typed.**
 * `DefaultCommandParser` never reads a declared default, and
 * `CommandContext.getOptionByLongName` reads the parsed input alone. So an
 * option that the caller left out is not in the context. This reader then
 * takes the declared option from the registered command, and answers its
 * default. Before `CHAT-xdpcnrde` it failed there, so every optional option was
 * required in practice.
 *
 * A typed value wins over the default. That ordering reproduces what
 * `@ShellOption(defaultValue = ...)` did.
 *
 * The long names are the contract. See `docs/SHELL-COMMAND-CONTRACT.md`.
 */
fun CommandContext.optionValue(longName: String): String {
    val typed = getOptionByLongName(longName)
    if (typed != null) return typed.value() ?: typed.defaultValue() ?: ""

    val declared = declaredOption(longName)
        ?: error("the command declares no option named $longName")
    return declared.defaultValue() ?: ""
}

/** The option as the registered command declares it, with its default. */
private fun CommandContext.declaredOption(longName: String): CommandOption? =
    commandRegistry().getCommandByName(parsedInput().commandName())
        ?.options
        ?.firstOrNull { it.longName() == longName }
