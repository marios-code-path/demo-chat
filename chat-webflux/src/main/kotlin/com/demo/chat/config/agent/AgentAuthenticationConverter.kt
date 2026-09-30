package com.demo.chat.config.agent

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import reactor.core.publisher.Mono

/** Converts a validated JWT into the configured agent authentication. */
class AgentAuthenticationConverter(
    private val identity: AgentIdentity,
    private val expectedClientId: String,
) : Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private val authoritiesConverter = JwtGrantedAuthoritiesConverter()

    override fun convert(jwt: Jwt): Mono<AbstractAuthenticationToken> {
        val clientId = jwt.claims["client_id"]
        if (clientId !is String || clientId != expectedClientId) {
            return Mono.error(
                BadCredentialsException("The token is not from the configured agent client.")
            )
        }

        return Mono.just(
            AgentAuthenticationToken(
                identity.principal(),
                jwt,
                authoritiesConverter.convert(jwt).orEmpty(),
            )
        )
    }
}
