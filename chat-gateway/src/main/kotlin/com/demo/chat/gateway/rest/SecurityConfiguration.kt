package com.demo.chat.gateway.rest

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers

@Configuration
@EnableWebFluxSecurity
class SecurityConfiguration {

    @Bean
    fun serverSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/chat/**"))
            .authorizeExchange{ exch ->
                exch
                    .pathMatchers("/chat/**")
                    .authenticated()
            }
            .authorizeExchange { exch ->
                exch
                    .pathMatchers("/chat/**")
                    .hasAuthority("SCOPE_chat.listen")
            }
            .oauth2ResourceServer()
            .jwt()

        return http.build()
    }

    @Bean
    fun jwtDecoder(@Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuerUri: String): ReactiveJwtDecoder {
        return ReactiveJwtDecoders.fromIssuerLocation(issuerUri)
    }
}