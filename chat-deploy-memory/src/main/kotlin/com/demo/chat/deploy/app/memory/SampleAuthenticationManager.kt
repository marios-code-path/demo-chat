package com.demo.chat.deploy.app.memory

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
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

class SampleAuthenticationManager(
    private val authenticationS: AuthenticationService<Long>,
    private val userPersistence: PersistenceStore<Long, User<Long>>,
    private val authorizationS: AuthorizationService<Long, AuthMetadata<Long>, AuthMetadata<Long>>
) :
    AuthenticationManager {
    override fun authenticate(authen: Authentication): Authentication {
        val credential = authen.credentials.toString()
        val targetId: Key<Long> = Key.funKey(if (authen.details is Long) authen.details as Long else 0L)

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