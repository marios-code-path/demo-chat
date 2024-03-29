package com.demo.chat.security.access

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.security.AccessBroker
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

class SpringSecurityAccessBrokerService<T>(
    val access: AccessBroker<T>,
    val rootKeys: RootKeys<T>
) {

    fun hasAccessToMany(targets: List<Key<T>>, perm: String): Mono<Boolean> =
        access.hasAccessManyByPrincipal(
            getSecurityContextPrincipal(),
            targets,
            perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    fun hasAccessToDomain(domain: String, perm: String): Mono<Boolean> =
        access.hasAccessByPrincipal(
            getSecurityContextPrincipal(),
            rootKeys.getRootKey(domain), perm
        )
            .doOnError { println("ERROR") }
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    fun hasAccessTo(who: T, target: T, perm: String): Mono<Boolean> =
        access.hasAccessByKeyId(who, target, perm)
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    fun hasAccessTo(target: Key<T>, perm: String): Mono<Boolean> =
        access.hasAccessByPrincipal(
            getSecurityContextPrincipal(),
            target, perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    fun <S> hasAccessToDomainByKind(kind: Class<S>, perm: String): Mono<Boolean> {
        if(!rootKeys.hasKey(kind))
            throw ChatException("Unknown key for domain ${kind.simpleName}")

       return access.hasAccessByPrincipal(
            getSecurityContextPrincipal(),
            rootKeys.getRootKey(kind), perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))
    }

    private fun getSecurityContextPrincipal() = ReactiveSecurityContextHolder.getContext()
        .map {
                it.authentication.principal as ChatUserDetails<T>
        }
        .switchIfEmpty(Mono.just(
            ChatUserDetails(User
                .create(rootKeys.getRootKey("Anon"), "anon", "anon", "http://anon"),
                listOf())
        ))
        .map { it.user.key }
}