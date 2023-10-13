package com.demo.chat.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.reactive.config.EnableWebFlux

@Configuration
@ComponentScan("com.demo.chat.controller.webflux")
@EnableWebFlux
@EnableWebFluxSecurity
class WebFluxSecurity {

    @Bean
    fun filterChain(http: ServerHttpSecurity): SecurityWebFilterChain? = http
        .authorizeExchange {

            it.anyExchange()
                .permitAll()
        }
        .cors { it.disable() }
        .csrf { it.disable() }
        .build()

}