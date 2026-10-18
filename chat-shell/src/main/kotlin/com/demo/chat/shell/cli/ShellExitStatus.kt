package com.demo.chat.shell.cli

import com.demo.chat.security.rsocket.CoreAuthenticationRefusal
import org.springframework.shell.core.command.CommandNotFoundException
import org.springframework.web.reactive.function.client.WebClientRequestException
import java.net.ConnectException

/**
 * The one exit status table of the shell modes.
 *
 * The one-shot process, the resident response, and the register client all
 * use these codes. The table is in the design, section "chat-shell as a
 * command tool", part 2.
 */
enum class ShellExitStatus(val code: Int) {
    OK(0),
    FAILED(1),
    USAGE(2),
    UNAVAILABLE(69),
    NOT_CONFIRMED(75),
    PROTOCOL(76),
    LOGIN(77),
    TIMEOUT(124);

    companion object {
        /**
         * The status of a failure.
         *
         * **The first cause that has a meaning wins, from the outside in.**
         * Spring Shell wraps every command error in a
         * `CommandExecutionException`, and a reactive call wraps again. So the
         * status reads the whole cause chain.
         */
        fun of(error: Throwable): ShellExitStatus {
            for (cause in causes(error)) {
                when (cause) {
                    is ShellExit -> return cause.status
                    is CoreAuthenticationRefusal -> return LOGIN
                    is ConnectException, is WebClientRequestException -> return UNAVAILABLE
                    is IllegalArgumentException, is CommandNotFoundException -> return USAGE
                }
            }
            return FAILED
        }

        /** The first line of the deepest cause message. A cause with no message gives its class name. */
        internal fun messageOf(error: Throwable): String {
            val cause = causes(error).lastOrNull { !it.message.isNullOrBlank() } ?: error
            return cause.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: cause.javaClass.simpleName
        }

        /** The error and its causes, outermost first. A cause that repeats ends the walk. */
        internal fun causes(error: Throwable): List<Throwable> {
            val chain = mutableListOf<Throwable>()
            var next: Throwable? = error
            while (next != null && chain.none { it === next }) {
                chain += next
                next = next.cause
            }
            return chain
        }
    }
}

/**
 * A failure that already knows its exit status.
 *
 * The message is the text for the `chat-shell:` line on standard error.
 */
class ShellExit(val status: ShellExitStatus, message: String) : RuntimeException(message)
