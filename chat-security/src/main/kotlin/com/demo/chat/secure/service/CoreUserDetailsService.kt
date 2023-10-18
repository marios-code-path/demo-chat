package com.demo.chat.secure.service

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import reactor.core.publisher.Mono

class CoreUserDetailsService<T>(
    private val userService: ChatUserService<T>,
    private val secretsStore: SecretsStore<T>,
    private val auth: AuthenticationService<T>,
) : ReactiveUserDetailsService, ReactiveUserDetailsPasswordService {

    override fun findByUsername(username: String): Mono<UserDetails> =
        userService.findByUsername(ByStringRequest(username))
            .switchIfEmpty(Mono.error(UsernameNotFoundException("User $username not found")))
            .map { ChatUserDetails(it, listOf("ROLE_USER")) }
            .next()
            .flatMap { user ->
                secretsStore
                    .getStoredCredentials(user.user.key)
                    .doOnNext { user.setPassword(it) }
                    .thenReturn(user)
            }

    override fun updatePassword(userDetails: UserDetails, newPassword: String): Mono<UserDetails> =
        userService.findByUsername(ByStringRequest(userDetails.username))
            .switchIfEmpty(Mono.error { UsernameNotFoundException(userDetails.username) })
            .next()
            .flatMap { user ->
                auth.setAuthentication(user.key, newPassword)
                    .map { userDetails }
            }
}