package com.demo.chat.shell.cli

import java.nio.file.Files
import java.nio.file.Path

/**
 * The login of the shell modes, from the environment.
 *
 * `CHAT_SHELL_USER` names the user. `CHAT_SHELL_PASSWORD_FILE` names a file
 * that holds the password. **A password never goes in a program argument**,
 * because `ps` shows arguments. A launch with neither variable logs in as it
 * does today, which is not at all.
 *
 * The file loses one trailing `\n` or `\r\n`, because `echo secret > file`
 * writes one. Nothing else changes, so a space in a password stays.
 *
 * The modes call [loginIfSet] once, before the first command. A failure is a
 * [ShellExit], and its message never holds the password.
 */
class EnvironmentLogin(private val env: (String) -> String?, private val login: (String, String) -> Unit) {

    fun loginIfSet() {
        val user = env(USER)?.ifEmpty { null }
        val file = env(PASSWORD_FILE)?.ifEmpty { null }
        if (user == null && file == null) return
        if (user == null || file == null)
            throw ShellExit(ShellExitStatus.USAGE, "set both $USER and $PASSWORD_FILE")

        val password = try {
            withoutLineEnd(Files.readString(Path.of(file)))
        } catch (e: Exception) {
            throw ShellExit(ShellExitStatus.LOGIN, "cannot read the password file $file")
        }
        if (password.isEmpty()) throw ShellExit(ShellExitStatus.LOGIN, "the password file $file is empty")

        try {
            login(user, password)
        } catch (e: Exception) {
            val reason = ShellExitStatus.causes(e).last().message ?: e.javaClass.simpleName
            // A core that is down is not a wrong password.
            val status = if (ShellExitStatus.of(e) == ShellExitStatus.UNAVAILABLE) ShellExitStatus.UNAVAILABLE
            else ShellExitStatus.LOGIN
            throw ShellExit(status, "Authentication failed: ${reason.lineSequence().first()}")
        }
    }

    private fun withoutLineEnd(text: String): String = when {
        text.endsWith("\r\n") -> text.dropLast(2)
        text.endsWith("\n") -> text.dropLast(1)
        else -> text
    }

    companion object {
        const val USER = "CHAT_SHELL_USER"
        const val PASSWORD_FILE = "CHAT_SHELL_PASSWORD_FILE"
    }
}
