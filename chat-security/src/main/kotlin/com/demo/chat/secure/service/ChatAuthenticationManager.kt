package com.demo.chat.secure.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.User
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import reactor.core.publisher.Mono

class ChatAuthenticationManager<T>(
    private val typeUtil: TypeUtil<T>,
    private val authenticationS: AuthenticationService<T>,
    private val userPersistence: PersistenceStore<T, User<T>>,
    private val authorizationS: AuthorizationService<T, AuthMetadata<T>>
) :
    AuthenticationManager {
    override fun authenticate(authen: Authentication): Authentication {
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
                )
            }
            .switchIfEmpty(Mono.error(BadCredentialsException("Invalid Credentials")))
            .block()!!
    }
}