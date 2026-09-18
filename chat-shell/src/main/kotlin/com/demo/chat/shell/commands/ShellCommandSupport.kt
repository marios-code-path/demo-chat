package com.demo.chat.shell.commands

import org.springframework.shell.core.command.CommandContext

/**
 * The option reader that every migrated command uses.
 *
 * **Spring Shell 4 does not bind options to method parameters.** The
 * declarative model did that, and it is gone. A programmatic command reads
 * each option from the context by its long name, so this is the one place
 * that knows how a value and a default combine.
 *
 * `CommandOption.value()` holds what the caller typed. It is null when the
 * caller typed nothing, and the declared default then applies. That ordering
 * reproduces what `@ShellOption(defaultValue = ...)` did.
 *
 * The long names are the contract. See `docs/SHELL-COMMAND-CONTRACT.md`.
 */
fun CommandContext.optionValue(longName: String): String {
    val option = getOptionByLongName(longName)
        ?: error("the command declares no option named $longName")
    return option.value() ?: option.defaultValue() ?: ""
}
