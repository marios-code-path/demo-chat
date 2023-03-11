package com.demo.chat.deploy.memory

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.web.server.SecurityWebFilterChain

// orphaned.
@Configuration(proxyBeanMethods = false)
class WebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf().disable()
            .formLogin().disable()
            .httpBasic().disable()
        http
            .authorizeExchange()
            .pathMatchers("/actuator/**").permitAll()
            .anyExchange().authenticated()

        return http.build()
    }

    @Bean
    fun passwordEncoder() = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}