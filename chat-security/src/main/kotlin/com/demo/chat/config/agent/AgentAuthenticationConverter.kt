package com.demo.chat.config.agent

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import reactor.core.publisher.Mono

/**
 * Converts a validated JWT into the authentication of the agent that its
 * `client_id` selects. See `CHAT-frcrctdp`.
 *
 * REST and the core use this class. Each side builds its own instance from its
 * own agent list.
 */
class AgentAuthenticationConverter(
    private val identities: AgentIdentities,
) : Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private val authoritiesConverter = JwtGrantedAuthoritiesConverter()

    override fun convert(jwt: Jwt): Mono<AbstractAuthenticationToken> {
        val clientId = jwt.claims["client_id"] as? String
        val principal = clientId?.let { identities.principalFor(it) }
            ?: return Mono.error(
                BadCredentialsException("The token is not from a configured agent client.")
            )

        return Mono.just(
            AgentAuthenticationToken(principal, jwt, authoritiesConverter.convert(jwt).orEmpty())
        )
    }
}
