package com.demo.chat.secure.access

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.security.AccessBroker
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono


@Component("chatAccess")
class SpringSecurityAccessBrokerService<T>(
    val access: AccessBroker<T>,
    val rootKeys: RootKeys<T>
) {

    fun hasAccessTo(target: Key<T>, perm: String): Mono<Boolean> =
        access.getAccessFromPublisher(
            getSecurityContextPrincipal(),
            target, perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    fun <S> hasAccessToDomain(kind: Class<S>, perm: String): Mono<Boolean> =
        access.getAccessFromPublisher(
            getSecurityContextPrincipal(),
            rootKeys.getRootKey(kind), perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    private fun getSecurityContextPrincipal() = ReactiveSecurityContextHolder.getContext()
        .map { it.authentication.principal as ChatUserDetails<T> }
        .map { it.user.key }
}