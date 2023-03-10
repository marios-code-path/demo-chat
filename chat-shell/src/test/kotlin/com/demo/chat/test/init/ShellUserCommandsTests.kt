package com.demo.chat.test.init

import com.demo.chat.shell.commands.UserCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class LongUserCommandsTests : ShellUserCommandsTests<Long>()

open class ShellUserCommandsTests<T> : ShellIntegrationTests() {

    @Test
    fun `should get key`(@Autowired userCommands: UserCommands<T>) {
        Assertions.assertThat(userCommands.key("false")).isNotNull
    }

    @Test
    fun `should get Users`(@Autowired userCommands: UserCommands<T>) {
        Assertions.assertThat(userCommands.users())
            .isNotNull
            .isNotBlank
            .containsAnyOf("Anon", "Admin")
    }

    @Test
    fun `should create user`(@Autowired userCommands: UserCommands<T>) {
        Assertions.assertThat(userCommands.addUser("Test", "TEST", "uri"))
            .isNotNull
            .isNotBlank
    }
}