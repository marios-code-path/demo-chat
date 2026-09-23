package com.demo.chat

import com.demo.chat.config.deploy.authserv.BlockingUserDetailsServiceConfiguration
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.service.CoreUserDetailsService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsPasswordService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The null password refusal, read through the blocking adapter.
 *
 * The owner approved the refusal on 2026-09-18, and
 * `CoreUserDetailsService.updatePassword` carries it.
 * `BlockingUserDetailsServiceConfiguration` passes the value through and
 * blocks. **This test reads the contract where a deployment reads it.**
 *
 * The adapter answers `error(...)` for an empty signal, which reports
 * `IllegalStateException`. So a refusal that became an empty signal would
 * still fail, and it would fail with a different type and a message that
 * names no cause. The assertions name the type for that reason.
 *
 * See CHAT-cophllrg.
 */
class BlockingUserDetailsPasswordServiceTests {

    @Test
    fun `should refuse a null password and write no credential`() {
        val store = RecordingAuthenticationService()

        assertThatThrownBy { passwordService(store).updatePassword(userDetails(), null) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(HANDLE)

        assertThat(store.written).isEmpty()
    }

    /**
     * The control for the test above.
     *
     * An assertion that no credential was written proves nothing on its own.
     * A fixture that can never record a write would satisfy it. This test
     * makes the same fixture record one.
     */
    @Test
    fun `should write the credential of a supplied password`() {
        val store = RecordingAuthenticationService()

        passwordService(store).updatePassword(userDetails(), PASSWORD)

        assertThat(store.written).containsExactly(PASSWORD)
    }

    private fun passwordService(auth: AuthenticationService<Long>): UserDetailsPasswordService =
        BlockingUserDetailsServiceConfiguration()
            .authServUserDetailsService(
                CoreUserDetailsService(StubUserService(), UnusedSecretsStore(), auth)
            ) as UserDetailsPasswordService

    private fun userDetails(): UserDetails = ChatUserDetails(user(), listOf("ROLE_USER"))

    private companion object {
        const val HANDLE = "TestHandle"
        const val PASSWORD = "a-supplied-password"

        fun user(): User<Long> =
            User.create(Key.funKey(1L), "TestUser", HANDLE, "http://test")
    }

    /** Records every credential that reaches the store. */
    private class RecordingAuthenticationService : AuthenticationService<Long> {
        val written: MutableList<String> = mutableListOf()

        override fun setAuthentication(uid: Key<Long>, pw: String): Mono<Void> {
            written.add(pw)
            return Mono.empty()
        }

        override fun authenticate(n: String, pw: String): Mono<out Key<Long>> =
            error("This test never authenticates")
    }

    private class StubUserService : ChatUserService<Long> {
        override fun findByUsername(req: ByStringRequest): Flux<out User<Long>> = Flux.just(user())

        override fun addUser(userReq: UserCreateRequest): Mono<out Key<Long>> =
            error("This test never adds a user")

        override fun findByUserId(req: ByIdRequest<Long>): Mono<out User<Long>> =
            error("This test never reads a user by id")
    }

    private class UnusedSecretsStore : SecretsStore<Long> {
        override fun getStoredCredentials(key: Key<Long>): Mono<String> =
            error("updatePassword never reads a stored credential")

        override fun addCredential(keyCredential: KeyCredential<Long>): Mono<Void> =
            error("updatePassword reaches the store through AuthenticationService")
    }
}
