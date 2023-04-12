package com.demo.chat.ui

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher


@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SecurityConfiguration(val clientRegistrationRepository: ReactiveClientRegistrationRepository) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain? {
        http
            .authorizeExchange { it
                .pathMatchers("/webjars/**").permitAll()
            }
            .authorizeExchange {
                it.anyExchange().authenticated()
            }
            .exceptionHandling { exc ->
                exc.authenticationEntryPoint(RedirectServerAuthenticationEntryPoint("/oauth2/authorization/chat-client-oidc"))
            }
            .oauth2Login { oauth2: ServerHttpSecurity.OAuth2LoginSpec ->
                oauth2
                    .authorizationRequestResolver(authorizationRequestResolver())
            }
            .oauth2Client(Customizer.withDefaults())
            .logout { logout ->
                logout.logoutSuccessHandler(oidcLogoutSuccessHandler())
            }

        return http.build()
    }

    private fun authorizationRequestResolver(): ServerOAuth2AuthorizationRequestResolver {
        val authorizationRequestMatcher: ServerWebExchangeMatcher = PathPatternParserServerWebExchangeMatcher(
            "/login/oauth2/authorization/chat-client-oidc"
        )

        return DefaultServerOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, authorizationRequestMatcher
        )
    }

    fun oidcLogoutSuccessHandler(): ServerLogoutSuccessHandler {
        val oidcLogoutSuccessHandler = OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository)

        // Set the location that the End-User's User Agent will be redirected to
        // after the logout has been performed at the Provider
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/index")

        return oidcLogoutSuccessHandler
    }
}