package com.demo.chat.test.init

import com.demo.chat.shell.commands.LoginCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired

class LongLoginCommandsTests : ShellLoginCommandsTests<Long>()

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ShellLoginCommandsTests<T>() : ShellIntegrationTests() {

    @Autowired
    private lateinit var loginCommands: LoginCommands<T>

    @Test
    @Order(1)
    fun `before login can whoami`() {
        Assertions.assertThat(loginCommands.whoami())
            .isNotNull
            .hasSizeGreaterThan(4)
    }

    @Test
    fun `should login succeed`() {
        Assertions.catchThrowable { loginCommands.login("Anon", "_") }
    }

    @Test
    fun `should login fail`() {
        Assertions.assertThatThrownBy { loginCommands.login("Anony", "_") }
    }

    @Test
    fun `after login should whoami succeed`() {
        loginCommands.login("Anon", "_")
        Assertions.assertThat(loginCommands.whoami())
            .isNotNull
            .isNotBlank
            .contains("Anon")
    }

}