package com.demo.chat.config.rsocket

import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.rsocket.api.PayloadExchange
import org.springframework.security.rsocket.api.PayloadInterceptor
import org.springframework.security.rsocket.api.PayloadInterceptorChain
import reactor.core.publisher.Mono

// RootKeys gets instantiated too much later. this would need to hook into that lifecycle.
//object ChatAnonymousAuthenticationTokenFactory {
//    fun <T> create(anonymousId: Key<T>): AbstractAuthenticationToken {
//        return object : AbstractAuthenticationToken(emptyList()) {
//            override fun getCredentials(): Any = "ANONYMOUS"
//            override fun getPrincipal(): Any = User
//                .create(
//                    anonymousId,
//                    "AnonymousUser",
//                    "ANONYMOUS",
//                    "http://images/anonymous.png"
//                )
//        }
//    }
//}
class DefaultingAnonymousPayloadInterceptor<T>(private val rootKeys: RootKeys<T>) : PayloadInterceptor {

    override fun intercept(exchange: PayloadExchange?, chain: PayloadInterceptorChain?): Mono<Void> {
        return ReactiveSecurityContextHolder.getContext()
            .filter { c: SecurityContext -> c.authentication != null }
            .doOnNext { securityContext ->
                val anonymousId = rootKeys.getRootKey(Anon::class.java)
                when (securityContext.authentication) {
                    is AnonymousAuthenticationToken -> securityContext.authentication =
                        ChatAnonymousAuthenticationToken(anonymousId)
                    else -> {}
                }
            }
            .then(chain!!.next(exchange))
    }
}