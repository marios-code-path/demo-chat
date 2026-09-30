package com.demo.chat.config.agent

import com.demo.chat.security.ChatUserDetails
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/** Carries the resolved agent principal and the validated JWT. */
class AgentAuthenticationToken(
    private val agentPrincipal: ChatUserDetails<*>,
    val jwt: Jwt,
    authorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = agentPrincipal

    override fun getName(): String = agentPrincipal.username
}
