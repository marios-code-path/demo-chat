package com.demo.chat.test.init

import com.demo.chat.shell.commands.UserCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired

@Disabled
class LongUserCommandsTests : ShellUserCommandsTests<Long>()

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
open class ShellUserCommandsTests<T> : ShellIntegrationTestBase() {

    @Autowired lateinit var userCommands: UserCommands<T>

    @Test
    fun `should get key`() {
        Assertions.assertThat(userCommands.key("false")).isNotNull
    }

    @Test
    fun `should get All Users`() {
        Assertions.assertThat(userCommands.users())
            .isNotNull
            .isNotBlank
            .containsAnyOf("Anon", "Admin")
    }

    @Test
    fun `should create user, fetch user`() {
        Assertions.assertThat(userCommands.addUser("Test", "TEST", "uri"))
            .isNotNull
            .isNotBlank

        Assertions.assertThat(userCommands.getUser("TEST"))
            .isNotNull
    }

    @Test
    fun `should get all permissions`() {
        val perms = userCommands.allPermissions()
        Assertions.assertThat(perms)
            .isNotNull
            .isNotBlank
    }

    @Test
    fun `should get user permissions`() {
        val perms = userCommands.getPermissionsForUser("_")
        Assertions.assertThat(perms)
            .isNotNull
            .isNotBlank
    }

    @Test
    fun `should find Admin user `() {
        val user = userCommands.findUser("Admin")
        Assertions.assertThat(user)
            .isNotNull
            .isNotBlank
        println(user)
    }


}