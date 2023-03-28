package com.demo.chat.deploy.memory

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration(proxyBeanMethods = false)
class ActuatorWebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .authenticationManager(ActuatorAuthenticationManager())
            .csrf().disable()
            .formLogin().disable()
            .httpBasic()
            .and()
            .authorizeExchange()
            .pathMatchers("/actuator/**")
            .hasRole("ADMIN")
            .anyExchange()
            .hasRole("ADMIN")

        return http.build()
    }

    @Bean
    fun passwordEncoder() = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}