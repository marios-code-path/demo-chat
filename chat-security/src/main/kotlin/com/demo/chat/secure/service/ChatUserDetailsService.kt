package com.demo.chat.secure.service

import com.demo.chat.domain.User
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Mono

open class ChatUserDetailsService<T, Q>(
    private val userPersist: PersistenceStore<T, User<T>>,
    private val userIndex: IndexService<T, User<T>, Q>,
    private val auth: AuthenticationService<T>,
    private val authZ: AuthorizationService<T, String>,
    val usernameQuery: (String) -> Q
) : ReactiveUserDetailsService, ReactiveUserDetailsPasswordService {
    override fun findByUsername(username: String): Mono<UserDetails> =
        userIndex.findUnique(usernameQuery(username))
            .flatMap(userPersist::get)
            .flatMap { user ->
                authZ.getAuthorizationsForPrincipal(user.key)
                    .collectList()
                    .map { authorizations -> ChatUserDetails(user, authorizations) }
            }

    override fun updatePassword(userDetails: UserDetails, newPassword: String): Mono<UserDetails> =
        userIndex.findUnique(usernameQuery(userDetails.username))
            .flatMap(userPersist::get)
            .flatMap { user ->
                auth.setAuthentication(user.key, newPassword)
                    .map { userDetails }
            }
}