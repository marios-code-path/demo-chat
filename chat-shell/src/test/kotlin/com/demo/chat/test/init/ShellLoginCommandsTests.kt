package com.demo.chat.test.init

import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.shell.commands.LoginCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired

@Tag("integration")
class LongLoginCommandsTests : ShellLoginCommandsTests<Long>()

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("integration")
class ShellLoginCommandsTests<T>() : ShellIntegrationTestBase() {

    @Autowired
    private lateinit var loginCommands: LoginCommands<T>

    @Test
    @Order(1)
    fun `before login can whoami`() {
        // The `Anon` floor. A caller that never logged in reads its own
        // identity, which is the `Anon` root key. See CHAT-ltvfmcvh.
        Assertions.assertThat(loginCommands.whoami())
            .isNotNull
            .hasSizeGreaterThan(4)
    }

    /**
     * **The deployed path judges the shipped Admin credential.**
     *
     * `login` resolves the user through the `user-by-handle` route, and that
     * call carries the metadata. A wrong password fails it, so a success here
     * means the server accepted this credential. The handle read back proves
     * the identity the shell stored.
     */
    @Test
    fun `an Admin login succeeds and whoami reports Admin`() {
        loginCommands.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)

        Assertions.assertThat(loginCommands.whoami())
            .isNotNull
            .contains(ShellDeploymentAccount.ADMIN_HANDLE)
    }

    /**
     * **A failed login leaves no credential and no identity.**
     *
     * The three assertions are one rule. A credential the secrets store does
     * not hold fails the call, the shell forgets both values, and the next
     * command runs on the `Anon` floor rather than on the identity the caller
     * tried to reach. Removal of the clearing in `login` fails this test.
     */
    @Test
    fun `a wrong password fails the login and leaves no login behind`() {
        Assertions.assertThatThrownBy {
            loginCommands.login(ShellDeploymentAccount.ADMIN_HANDLE, "not-the-shipped-password")
        }
            .describedAs("the refusal of a credential that the secrets store does not hold")

        Assertions.assertThat(ShellStateConfiguration.loginMetadata)
            .describedAs("the credential after a failed login")
            .isEmpty

        Assertions.assertThat(ShellStateConfiguration.loggedInUser)
            .describedAs("the identity after a failed login")
            .isEmpty

        Assertions.assertThat(loginCommands.whoami())
            .isNotNull
            .doesNotContain(ShellDeploymentAccount.ADMIN_HANDLE)
    }

    @Test
    fun `should login succeed`() {
        Assertions.assertThatCode { loginCommands.login("Anon", "_") }
            .describedAs("the shipped Anon credential")
            .doesNotThrowAnyException()

        Assertions.assertThat(ShellStateConfiguration.loginMetadata)
            .describedAs("the credential after a successful login")
            .isPresent
    }

    @Test
    fun `should login fail`() {
        Assertions.assertThatThrownBy { loginCommands.login("Anony", "_") }

        Assertions.assertThat(ShellStateConfiguration.loginMetadata).isEmpty
        Assertions.assertThat(ShellStateConfiguration.loggedInUser).isEmpty
    }

    @Test
    fun `after login should whoami succeed`() {
        loginCommands.login("Anon", "_")
        Assertions.assertThat(loginCommands.whoami())
            .isNotNull
            .isNotBlank
            .contains(Anon::class.java.simpleName)
    }

}
