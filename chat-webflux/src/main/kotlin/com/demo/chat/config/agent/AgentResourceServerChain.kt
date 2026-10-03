package com.demo.chat.config.agent

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono

/** Builds the authenticated application chain for one deployment. */
class AgentResourceServerChain(
    private val properties: AgentSecurityProperties,
    private val decoder: ReactiveJwtDecoder,
    private val converter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
) {

    /** The authority that every application route requires. */
    fun requiredAuthority(): String = authorityFor(properties.requireComplete().agent.requiredScope)

    fun build(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .authorizeExchange {
            it.anyExchange().hasAuthority(requiredAuthority())
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt ->
                jwt.jwtAuthenticationConverter(converter)
                jwt.jwtDecoder(decoder)
            }
        }
        .cors { it.disable() }
        .csrf { it.disable() }
        .build()

    companion object {
        /** The prefix that scope authorities use. */
        const val SCOPE_PREFIX = "SCOPE_"

        fun authorityFor(scope: String): String = "$SCOPE_PREFIX$scope"
    }
}
