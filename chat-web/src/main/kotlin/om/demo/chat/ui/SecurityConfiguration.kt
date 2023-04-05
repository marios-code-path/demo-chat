package om.demo.chat.ui

import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers


@EnableWebFluxSecurity
class SecurityConfiguration(val clientRegistrationRepository: ReactiveClientRegistrationRepository) {

    /**
     * 		http
     * 			.authorizeHttpRequests(authorize ->
     * 				authorize.anyRequest().authenticated()
     * 			)
     * 			.oauth2Login(oauth2Login ->
     * 				oauth2Login.loginPage("/oauth2/authorization/messaging-client-oidc"))
     * 			.oauth2Client(withDefaults())
     * 			.logout(logout ->
     * 				logout.logoutSuccessHandler(oidcLogoutSuccessHandler()));
     * 		return http.build();
     */

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/chat/**"))
            .authorizeExchange { exch ->
                exch.anyExchange()
                    .authenticated()
            }
            .oauth2Login { } // LoginPageGeneratingWebFilter
            .oauth2Client(Customizer.withDefaults())
            .logout { logout ->
                logout.logoutSuccessHandler(oidcLogoutSuccessHandler())
            }

        return http.build()
    }

    fun oidcLogoutSuccessHandler(): ServerLogoutSuccessHandler {
        val oidcLogoutSuccessHandler = OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository)

        // Set the location that the End-User's User Agent will be redirected to
        // after the logout has been performed at the Provider

        // Set the location that the End-User's User Agent will be redirected to
        // after the logout has been performed at the Provider
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/index")

        return oidcLogoutSuccessHandler
    }

}