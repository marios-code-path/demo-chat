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
    private val persist: PersistenceStore<T, User<T>>,
    private val index: IndexService<T, User<T>, Q>,
    private val auth: AuthenticationService<T>,
    private val authZ: AuthorizationService<T, String>,
    val usernameQuery: (String) -> Q
) : ReactiveUserDetailsService, ReactiveUserDetailsPasswordService {
    override fun findByUsername(username: String): Mono<UserDetails> =
        index.findUnique(usernameQuery(username))
            .flatMap(persist::get)
            .flatMap { user ->
                authZ.getAuthorizationsForPrincipal(user.key)
                    .collectList()
                    .map { authorizations -> ChatUserDetails(user, authorizations) }
            }

    override fun updatePassword(userDetails: UserDetails, newPassword: String): Mono<UserDetails> =
        index.findUnique(usernameQuery(userDetails.username))
            .flatMap(persist::get)
            .flatMap { user ->
                auth.setAuthentication(user.key, newPassword)
                    .map { userDetails }
            }
}