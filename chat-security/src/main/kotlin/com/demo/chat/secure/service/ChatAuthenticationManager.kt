package com.demo.chat.secure.service

import com.demo.chat.domain.User
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.AuthenticationService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import reactor.core.publisher.Mono

class ChatAuthenticationManager<T>(
    private val authenticationS: AuthenticationService<T>,
    private val userPersistence: PersistenceStore<T, User<T>>,
) :
    ReactiveAuthenticationManager {
    override fun authenticate(authen: Authentication): Mono<Authentication> {
        val credential = authen.credentials.toString()

        return authenticationS
            .authenticate(authen.name, credential)
            .onErrorMap { thr -> InternalAuthenticationServiceException(thr.message, thr) }
            .flatMap(userPersistence::get)
            .map { user ->
                val userDetails = ChatUserDetails(user, listOf())
                UsernamePasswordAuthenticationToken(
                    userDetails,
                    authen.credentials,
                    userDetails.authorities
                )as Authentication
            }
            .switchIfEmpty(Mono.error(BadCredentialsException("Invalid Credentials")))

    }
}