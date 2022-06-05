package com.demo.chat.secure.service
import com.demo.chat.domain.Key
import com.demo.chat.domain.UsernamePasswordAuthenticationException
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.IndexService
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
open class AbstractAuthenticationService<T, U, Q>(
    private val userIndex: IndexService<T, U, Q>,
    private val secretsStore: SecretsStore<T>,
    private val passwordValidator: BiFunction<String, String, Boolean>,
    private val userNameToQuery: Function<String, Q>
) : AuthenticationService<T> {
    override fun setAuthentication(pKey: Key<T>, pw: String): Mono<Void> = secretsStore.addCredential(pKey, pw)

    override fun authenticate(n: String, pw: String): Mono<out Key<T>> =
        userIndex
            .findUnique(userNameToQuery.apply(n))
            .flatMap { userKey ->
                secretsStore
                    .getStoredCredentials(userKey)
                    .map { secure ->
                        if (!passwordValidator.apply(pw, secure)) throw UsernamePasswordAuthenticationException
                        userKey
                    }
            }
            .switchIfEmpty(Mono.error(UsernamePasswordAuthenticationException))
}