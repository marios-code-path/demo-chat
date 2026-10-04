package com.demo.chat.shell.commands

import org.springframework.shell.core.command.CommandArgument
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
 * **A required option that the caller left out is refused.** Before
 * `CHAT-lasqmeib` it answered an empty text, so `add-topic lobby` sent a room
 * with no name.
 *
 * The long names are the contract. See `docs/SHELL-COMMAND-CONTRACT.md`.
 */
fun CommandContext.optionValue(longName: String): String {
    val typed = getOptionByLongName(longName)
    if (typed != null) return typed.value() ?: typed.defaultValue() ?: ""

    val declared = declaredOption(longName)
        ?: error("the command declares no option named $longName")
    if (declared.required() == true && declared.defaultValue() == null)
        throw IllegalArgumentException("${commandName()} needs --$longName.")
    return declared.defaultValue() ?: ""
}

/**
 * The value of the main option of a command, typed or given by position.
 *
 * **A command takes its main option as its first bare word.** So
 * `add-topic lobby` reads as `add-topic --name lobby`. The parser already
 * adds each bare word as an argument, and no command read one before
 * `CHAT-lasqmeib`.
 *
 * A command takes one bare word. A value with spaces needs quotes. A value
 * given both ways is refused, because the shell cannot choose one.
 */
fun CommandContext.mainValue(longName: String): String {
    val words = parsedInput().arguments()
    if (words.size > 1)
        throw IllegalArgumentException("${commandName()} takes one argument. Put quotes around a value that has spaces.")
    val word = words.firstOrNull()?.value()
    val typed = getOptionByLongName(longName)

    if (word != null && typed != null)
        throw IllegalArgumentException("Give $longName one time, as --$longName or as the first argument.")
    if (word != null) return word
    if (typed == null && declaredOption(longName)?.let { it.required() == true && it.defaultValue() == null } == true)
        throw IllegalArgumentException("${commandName()} needs $longName. Give it as the first argument or as --$longName.")
    return optionValue(longName)
}

/**
 * The declaration of the bare word that fills --[longName].
 *
 * It puts the word in the command help. The contract test reads it to pin the
 * main option of each command.
 */
fun positional(longName: String): CommandArgument =
    CommandArgument.with().index(0).description("Same as --$longName").type(String::class.java).build()

private fun CommandContext.commandName(): String = parsedInput().commandName()

/** The option as the registered command declares it, with its default. */
private fun CommandContext.declaredOption(longName: String): CommandOption? =
    commandRegistry().getCommandByName(parsedInput().commandName())
        ?.options
        ?.firstOrNull { it.longName() == longName }
