package com.demo.chat.security.service

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import reactor.core.publisher.Mono
import java.util.function.Supplier

/**
 * The user details that the RSocket and web seams authenticate against.
 *
 * **Every user holds `ROLE_USER`.** Two more roles gate the core RSocket
 * routes. See `CHAT-rdlghoqe` and `docs/ANONYMOUS-AUTHORIZATION.md`.
 *
 * - `ROLE_ADMIN` goes to the user whose key [adminKey] supplies. The key is
 *   read at each login, because root keys load after singleton creation.
 * - `ROLE_SERVICE` goes to each user whose handle is in [serviceHandles]. A
 *   service account is a plain user, so `ChatIdentity` stays closed.
 *
 * A null [adminKey] and an empty [serviceHandles] give `ROLE_USER` alone.
 */
class CoreUserDetailsService<T>(
    private val userService: ChatUserService<T>,
    private val secretsStore: SecretsStore<T>,
    private val auth: AuthenticationService<T>,
    private val adminKey: Supplier<Key<T>>? = null,
    private val serviceHandles: Set<String> = emptySet(),
) : ReactiveUserDetailsService, ReactiveUserDetailsPasswordService {

    override fun findByUsername(username: String): Mono<UserDetails> =
        userService.findByUsername(ByStringRequest(username))
            .switchIfEmpty(Mono.error(UsernameNotFoundException("User $username not found")))
            .map { ChatUserDetails(it, rolesOf(it)) }
            .next()
            .flatMap { user ->
                secretsStore
                    .getStoredCredentials(user.user.key)
                    .doOnNext { user.setPassword(it) }
                    .thenReturn(user)
            }

    private fun rolesOf(user: User<T>): List<String> =
        listOfNotNull(
            "ROLE_USER",
            ROLE_ADMIN.takeIf { adminKey != null && user.key.id == adminKey.get().id },
            ROLE_SERVICE.takeIf { user.handle in serviceHandles },
        )

    companion object {
        const val ROLE_ADMIN = "ROLE_ADMIN"
        const val ROLE_SERVICE = "ROLE_SERVICE"
    }

    /**
     * Replaces the stored credential of one user.
     *
     * Spring Security 7 declares the new password as nullable, and Spring
     * Security 6 left it unannotated. **This method refuses a null.**
     * `AuthenticationService.setAuthentication` takes a non-null password, and
     * a credential store that accepted a null would hold a password that no
     * caller chose.
     *
     * The refusal is a decision rather than a repair. See CHAT-wuftjuvt.
     */
    override fun updatePassword(userDetails: UserDetails, newPassword: String?): Mono<UserDetails> =
        Mono.justOrEmpty(newPassword)
            .switchIfEmpty(Mono.error(IllegalArgumentException(
                "A null password cannot replace the credential of ${userDetails.username}"
            )))
            .flatMap { password -> replaceCredential(userDetails, password) }

    /**
     * `AuthenticationService.setAuthentication` answers `Mono<Void>`, which
     * completes empty. **So `map` here emitted nothing**, and the whole
     * success path answered an empty signal. The blocking adapter in
     * `chat-authorization-server` reports `IllegalStateException` for an
     * empty signal, so every accepted password upgrade failed there.
     * `thenReturn` answers the details after the write completes.
     */
    private fun replaceCredential(userDetails: UserDetails, newPassword: String): Mono<UserDetails> =
        userService.findByUsername(ByStringRequest(userDetails.username))
            .switchIfEmpty(Mono.error { UsernameNotFoundException(userDetails.username) })
            .next()
            .flatMap { user ->
                auth.setAuthentication(user.key, newPassword)
                    .thenReturn(userDetails)
            }
}