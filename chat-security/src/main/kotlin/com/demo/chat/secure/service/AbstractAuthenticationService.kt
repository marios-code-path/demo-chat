package com.demo.chat.secure.service
import com.demo.chat.domain.Key
import com.demo.chat.domain.UsernamePasswordAuthenticationException
import com.demo.chat.security.AuthenticationService
import com.demo.chat.service.IndexService
import com.demo.chat.security.SecretsStore
import reactor.core.publisher.Mono
import java.util.function.BiFunction
import java.util.function.Function

// E == username type
// T == key type
// U == UserObject type(???)
// V == password type
// Q == searchQuery Type(???)
open class AbstractAuthenticationService<E, T, U, V, Q>(
    private val userIndex: IndexService<T, U, Q>,
    private val secretsStore: SecretsStore<T, V>,
    private val passwordValidator: BiFunction<V, V, Boolean>,
    private val userHandleToQuery: Function<E, Q>
) : AuthenticationService<T, E, V> {
    override fun setAuthentication(pKey: Key<T>, pw: V): Mono<Void> = secretsStore.addCredential(pKey, pw)

    override fun authenticate(n: E, pw: V): Mono<out Key<T>> =
        userIndex
            .findUnique(userHandleToQuery.apply(n))
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