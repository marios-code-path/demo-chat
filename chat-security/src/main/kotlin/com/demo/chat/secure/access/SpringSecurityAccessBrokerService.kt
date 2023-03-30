package com.demo.chat.secure.access

import com.demo.chat.domain.Key
import com.demo.chat.secure.ChatUserDetails
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono


open class SpringSecurityAccessBrokerService<T>(val access: AccessBroker<T>) {

    open fun hasAccessFor(target: Key<T>, perm: String): Mono<Boolean> =
        access.getAccessFromPublisher(
            ReactiveSecurityContextHolder.getContext()
                .map { it.authentication.principal as ChatUserDetails<T> }
                .map { it.user.key },
            target, perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))
}