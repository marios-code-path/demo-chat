package com.demo.chat.secure

import com.demo.chat.domain.User
import com.demo.chat.service.AuthenticationService
import com.demo.chat.service.AuthorizationService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Mono

open class ChatUserDetailsService<T, E, Q>(
    private val persist: PersistenceStore<T, User<T>>,
    private val index: IndexService<T, User<T>, Q>,
    private val auth: AuthenticationService<T, E, String>,
    private val authZ: AuthorizationService<T, String>,
    val usernameQuery: (String) -> Q
) : ReactiveUserDetailsService, ReactiveUserDetailsPasswordService {
    override fun findByUsername(username: String): Mono<UserDetails> =
        index.findOneBy(usernameQuery(username))
            .flatMap(persist::get)
            .flatMap { user ->
                authZ.findAuthorizationsFor(user.key.id)
                    .collectList()
                    .map { authorizations -> ChatUserDetails(user, authorizations) }
            }

    override fun updatePassword(userDetails: UserDetails, newPassword: String): Mono<UserDetails> =
        index.findOneBy(usernameQuery(userDetails.username))
            .flatMap(persist::get)
            .flatMap { user ->
                auth.createAuthentication(user.key.id, newPassword)
                    .map { userDetails }
            }
}