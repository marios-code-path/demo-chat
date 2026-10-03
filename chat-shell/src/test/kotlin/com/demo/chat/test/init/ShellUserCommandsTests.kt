package com.demo.chat.test.init

import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.UserCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Tag("integration")
class LongUserCommandsTests : ShellUserCommandsTests<Long>()

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("integration")
open class ShellUserCommandsTests<T : Any> : ShellIntegrationTestBase() {

    @Autowired lateinit var userCommands: UserCommands<T>

    @Autowired lateinit var loginCommands: LoginCommands<T>

    /**
     * **A shell command that calls a core route needs an Admin login.** The
     * core RSocket routes require `ROLE_SERVICE` or `ROLE_ADMIN` since
     * 2026-10-02. `kv` writes through `persist.keyvalue`. See `CHAT-rdlghoqe`.
     */
    @Test
    fun `an anonymous caller cannot reach a core route`() {
        Assertions.assertThatThrownBy { userCommands.kv("anondata") }
            .describedAs("the refusal of a core route for a caller with no role")
            .hasMessageContaining("The core refused authorization.")
    }

    @Test
    fun `should create kv`() {
        loginAsAdmin()
        Assertions.assertThat(userCommands.kv("data")).isNotNull
    }

    @Test
    fun `should add and get`() {
        loginAsAdmin()
        val newKey = userCommands.kv("data2")
        val getKv = userCommands.getKV(newKey!!.id)

        Assertions
            .assertThat(getKv)
            .isNotNull

        Assertions
            .assertThat(getKv?.contains(newKey!!.id.toString()))
            .isNotNull

        Assertions
            .assertThat(getKv?.contains("data2"))
            .isTrue
    }

    @Test
    fun `should get key`() {
        Assertions.assertThat(userCommands.key()).isNotNull
    }

    @Test
    fun `should get All Users`() {
        loginAsAdmin()
        Assertions.assertThat(userCommands.users())
            .isNotNull
            .isNotBlank
            .containsAnyOf("Anon", "Admin")
    }

    /**
     * **A caller with no credential cannot create a user.**
     *
     * `addUser` carries the `User NEW` check, and no shipped row grants that
     * permission to the `Anon` key or to the `User` root. Only the Admin
     * wildcard row reaches it. So this refusal is the control that the test
     * below turns on. A control that never records the refusal would satisfy
     * the Admin test on its own.
     */
    @Test
    fun `an anonymous caller cannot create a user`() {
        Assertions.assertThatThrownBy { userCommands.addUser("Test", "TESTCTRL", "uri") }
            .describedAs("the refusal of User NEW for a caller that holds no Admin row")
            .hasMessageContaining("Access Denied")
    }

    /**
     * **The Admin identity crosses the RSocket seam and it is what allows the
     * write.**
     *
     * The three steps are one chain. The login presents the shipped Admin
     * credential, the server judges it on the `user-by-handle` call, and the
     * `user-add` call that follows carries the metadata again. Only that
     * identity answers `User NEW`. See `CHAT-wbcbptiq`.
     */
    @Test
    fun `should create user, fetch user`() {
        loginCommands.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)

        Assertions.assertThat(userCommands.addUser("Test", "TEST", "uri"))
            .isNotNull
            .isNotBlank

        Assertions.assertThat(userCommands.getUser("TEST"))
            .isNotNull
    }

    @Test
    fun `should get all permissions`() {
        loginAsAdmin()
        val perms = userCommands.allPermissions()
        Assertions.assertThat(perms)
            .isNotNull
            .isNotBlank
    }

    @Test
    fun `should get user permissions`() {
        loginAsAdmin()
        val perms = userCommands.getPermissionsForUser("_")
        Assertions.assertThat(perms)
            .isNotNull
            .isNotBlank
    }

    @Test
    fun `should find Admin user `() {
        loginAsAdmin()
        val user = userCommands.findUser("Admin")
        Assertions.assertThat(user)
            .isNotNull
            .isNotBlank
    }

    private fun loginAsAdmin() {
        loginCommands.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
    }
}
