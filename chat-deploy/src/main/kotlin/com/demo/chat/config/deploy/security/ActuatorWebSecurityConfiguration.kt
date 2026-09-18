package com.demo.chat.config.deploy.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.security.autoconfigure.actuate.web.reactive.EndpointRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * The actuator routes, and only the actuator routes.
 *
 * This chain answered anyExchange before. It therefore owned every route of
 * every deployment that carried it, and it demanded the ACTUATOR role on
 * application routes. It also installed an authentication manager over one
 * in-memory actuator user, so no other user could pass it. See CHAT-jdsamcia.
 *
 * The matcher reads the management configuration and does not name a path. An
 * operator can set management.endpoints.web.base-path, and the actuator routes
 * then arrive on another prefix. A matcher on a literal actuator path would
 * miss them, and the application chain would answer them.
 *
 * Do not write a literal actuator path pattern in a comment here. Kotlin nests
 * a block comment, so the opening delimiter inside such a pattern starts a
 * nested comment and the file stops compiling.
 *
 * The order is explicit on both chains. A default order would leave the winner
 * to bean ordering, which no code states.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
class ActuatorWebSecurityConfiguration(
    @Value("\${app.actuator.username:actuator}") val actuatorUser: String,
    @Value("\${app.actuator.password:actuator}") val actuatorPasswd: String,
    private val passwordEncoder: PasswordEncoder
) {

    @Bean
    @Order(ACTUATOR_CHAIN_ORDER)
    fun actuatorSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authenticationManager(
                UserDetailsRepositoryReactiveAuthenticationManager(actuatorUserDetailService())
            )
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { }
            .authorizeExchange {
                it.matchers(EndpointRequest.to("health")).permitAll()
                it.anyExchange().hasRole("ACTUATOR")
            }

        return http.build()
    }

    fun actuatorUserDetailService() = MapReactiveUserDetailsService(
        org.springframework.security.core.userdetails.User
            .withUsername(actuatorUser)
            .password(passwordEncoder.encode(actuatorPasswd))
            .roles("ACTUATOR")
            .build()
    )

    companion object {
        /**
         * The actuator chain runs first. Its matcher is narrow, so a request
         * that it does not match falls through to the application chain.
         */
        const val ACTUATOR_CHAIN_ORDER = Ordered.HIGHEST_PRECEDENCE + 100
    }
}
