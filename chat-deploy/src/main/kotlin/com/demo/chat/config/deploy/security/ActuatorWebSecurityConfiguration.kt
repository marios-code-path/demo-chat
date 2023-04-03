package com.demo.chat.config.deploy.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AbstractUserDetailsReactiveAuthenticationManager
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain

// TODO Security best practices
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
class ActuatorWebSecurityConfiguration(
    @Value("\${app.actuator.username:actuator}") val actuatorUser: String,
    @Value("\${app.actuator.password:actuator}") val actuatorPasswd: String
) {

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .authenticationManager(UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService()))
            .csrf().disable()
            .formLogin().disable()
            .httpBasic()
            .and()
            .authorizeExchange()
            .pathMatchers("/actuator/health").permitAll()
            .pathMatchers("/actuator/**")
            .hasRole("ACTUATOR")
            .anyExchange()
            .hasRole("ACTUATOR")

        return http.build()
    }

    fun userDetailsService() = MapReactiveUserDetailsService(
        org.springframework.security.core.userdetails.User
            .withUsername(actuatorUser)
            .password(passwordEncoder().encode(actuatorPasswd))
            .roles("ACTUATOR")
            .build()
    )

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}