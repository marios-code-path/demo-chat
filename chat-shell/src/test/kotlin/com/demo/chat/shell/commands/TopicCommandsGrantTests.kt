package com.demo.chat.shell.commands

import com.demo.chat.service.security.AuthorizationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The shell writes no ownership row.
 *
 * **`*` is singular per target, and the server is the only writer.** Two
 * writers would give one room two owners. See `CHAT-zhjltbky`.
 */
class TopicCommandsGrantTests {

    /**
     * The authorization service of the shell is never called, because the
     * class does not hold one. This test pins the absence by reading the
     * constructor of the deployed class.
     *
     * A shell that cannot present an identity owns no room. Nothing breaks
     * today, because no deployment wires a check.
     */
    @Test
    fun `the shell holds no authorization service`() {
        val parameters = TopicCommands::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }

        assertThat(parameters).doesNotContain(AuthorizationService::class.java)
    }
}
