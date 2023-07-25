package com.demo.chat.secure.service

import com.demo.chat.domain.Key
import com.demo.chat.domain.UsernamePasswordAuthenticationException
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import reactor.core.publisher.Mono
import java.util.function.BiFunction
import java.util.function.Function

/**
 * Authentication Service for which
 * T = Key Type
 * U = User Type
 * Q = Username search Type
 */
open class CoreAuthenticationService<T, U, Q>(
    private val userIndex: IndexService<T, U, Q>,
    private val secretsStore: SecretsStore<T>,
    private val passwordValidator: BiFunction<String, String, Boolean>,
    private val userNameToQuery: Function<String, Q>,
    private val rootKeys: RootKeys<T>,
) : AuthenticationService<T> {

    override fun setAuthentication(uid: Key<T>, pw: String): Mono<Void> =
        secretsStore.addCredential(KeyCredential(uid, pw))

    override fun authenticate(n: String, pw: String): Mono<out Key<T>> =
        userIndex
            .findUnique(userNameToQuery.apply(n))
            .switchIfEmpty(Mono.error(UsernamePasswordAuthenticationException))
            .flatMap { userKey ->  // this should only happen when rootKeys is there!!!
                if (rootKeys.hasRootKey(Anon::class.java, userKey))
                    Mono.just(userKey)
                else
                    secretsStore
                        .getStoredCredentials(userKey)
                        .map { secure ->
                            if (!passwordValidator.apply(pw, secure)) throw UsernamePasswordAuthenticationException
                            userKey
                        }
            }
}