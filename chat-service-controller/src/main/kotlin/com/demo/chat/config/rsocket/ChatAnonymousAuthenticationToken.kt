package com.demo.chat.config.rsocket

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import org.springframework.security.authentication.AbstractAuthenticationToken

class ChatAnonymousAuthenticationToken<T>(private var anonymousId: Key<T>) : AbstractAuthenticationToken(emptyList()) {
    override fun getCredentials(): Any = "ANONYMOUS"
    override fun getPrincipal(): Any = User.create(
        anonymousId,
        "AnonymousUser",
        "ANONYMOUS",
        "http://images/anonymous.png"
    )
}