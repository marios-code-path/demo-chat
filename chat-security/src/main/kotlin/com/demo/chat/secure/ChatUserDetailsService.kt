package com.demo.chat.secure

import com.demo.chat.domain.User
import com.demo.chat.service.AuthService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Mono

open class ChatUserDetailsService<T, Q>(
    private val persist: PersistenceStore<T, User<T>>,
    private val index: IndexService<T, User<T>, Q>,
    private val auth: AuthService<T>,
    val usernameQuery: (String) -> Q
) : ReactiveUserDetailsService, ReactiveUserDetailsPasswordService {
    override fun findByUsername(username: String): Mono<UserDetails> =
        Mono.from(index.findBy(usernameQuery(username)).take(1))
            .flatMap(persist::get)
            .flatMap { user ->
                auth.findAuthorizationsFor(user.key.id)
                    .collectList()
                    .map { authorizations -> ChatUserDetails(user, authorizations) }
            }

    override fun updatePassword(user: UserDetails, newPassword: String): Mono<UserDetails> {
        return if (user is ChatUserDetails<*>)
            auth.createAuthentication(user.key.id as T, newPassword) // it casts... boooo
                .map { user }
        else
            Mono.empty()
    }
}