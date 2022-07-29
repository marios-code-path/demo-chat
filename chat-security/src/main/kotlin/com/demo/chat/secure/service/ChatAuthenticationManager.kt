package com.demo.chat.secure.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.User
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import reactor.core.publisher.Mono
import java.util.stream.Collectors

class ChatAuthenticationManager<T>(
    private val typeUtil: TypeUtil<T>,
    private val authenticationS: AuthenticationService<T>,
    private val userPersistence: PersistenceStore<T, User<T>>,
    private val authorizationS: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>
) :
    AuthenticationManager {
    override fun authenticate(authen: Authentication): Authentication {
        val credential = authen.credentials.toString()
        val targetId: Key<T> = Key.funKey(typeUtil.assignFrom(authen.details))

        return authenticationS
            .authenticate(authen.name, credential)
            .onErrorMap { thr -> InternalAuthenticationServiceException(thr.message, thr) }
            .flatMap(userPersistence::get)
            .flatMap { user ->
                authorizationS
                    .getAuthorizationsAgainst(user.key, targetId)
                    .map { authMeta -> authMeta.permission }
                    .collect(Collectors.toList())
                    .map { authorizations ->
                        val userDetails = ChatUserDetails(user, authorizations)
                        UsernamePasswordAuthenticationToken(
                            userDetails,
                            authen.credentials,
                            userDetails.authorities
                        ).apply {
                            details = authen.details
                        }
                    }
            }
            .switchIfEmpty(Mono.error(BadCredentialsException("Invalid Credentials")))
            .block()!!
    }
}