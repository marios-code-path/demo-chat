package com.demo.chat.config.deploy.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(ServerHttpSecurity::class)
class ActuatorWebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .authenticationManager(ActuatorAuthenticationManager())
            .csrf().disable()
            .formLogin().disable()
            //.httpBasic()
            //.and()
            .authorizeExchange()
            .pathMatchers("/actuator/**")
            .permitAll()
            //.hasRole("ADMIN")
            .anyExchange()
            .permitAll()
            //.hasRole("ADMIN")

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}