package com.demo.chat.test.init

import com.demo.chat.client.rsocket.EmptyRequestMetadata
import com.demo.chat.client.rsocket.MetadataRSocketRequester
import com.demo.chat.client.rsocket.RSocketRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.client.rsocket.SimpleRequestMetadata
import com.demo.chat.shell.commands.LoginCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import reactor.test.StepVerifier
import java.util.function.Supplier

@Tag("integration")
class ShellRequesterTests : ShellIntegrationTestBase() {

    @Test
    fun `metadataprovider supplies requestmetadata`(@Autowired sup: Supplier<RequestMetadata>) {
        Assertions
            .assertThat(sup)
            .isNotNull

        Assertions
            .assertThat(sup.get())
            .isInstanceOf(RequestMetadata::class.java)
    }

    /**
     * **The provider reads the login state on every route call, so the
     * credential survives into later commands.**
     *
     * `MetadataRSocketRequester.route` asks this provider each time, and
     * `ShellStateConfiguration` builds the answer from the stored login. So a
     * login at the start of a session reaches every command after it, and a
     * caller that never logged in sends no credential.
     */
    @Test
    fun `the metadata provider carries the credential after a login`(
        @Autowired sup: Supplier<RequestMetadata>,
        @Autowired loginCommands: LoginCommands<Long>,
    ) {
        Assertions.assertThat(sup.get())
            .describedAs("the metadata of a caller that never logged in")
            .isSameAs(EmptyRequestMetadata)

        loginCommands.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)

        val metadata = sup.get()
        Assertions.assertThat(metadata)
            .describedAs("the metadata of a logged-in caller")
            .isInstanceOf(SimpleRequestMetadata::class.java)

        val value = (metadata as SimpleRequestMetadata).value
        Assertions.assertThat(value).isInstanceOf(UsernamePasswordMetadata::class.java)
        Assertions.assertThat((value as UsernamePasswordMetadata).username)
            .describedAs("the handle the provider presents")
            .isEqualTo(ShellDeploymentAccount.ADMIN_HANDLE)
    }

    @Test
    fun `requester sends RequestMetadata`(@Autowired factory: RSocketRequesterFactory) {
        val requester = factory.getClientForService("user")

        Assertions
            .assertThat(requester)
            .isNotNull
            .isInstanceOf(MetadataRSocketRequester::class.java)

        StepVerifier
            .create(requester.route("test").send())
            .verifyComplete()
    }
}