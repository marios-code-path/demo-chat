package com.demo.chat.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.reactive.config.EnableWebFlux

/**
 * The application routes, which is every route the actuator chain does not
 * own.
 *
 * The order is explicit. The actuator chain runs first and matches the
 * actuator routes alone. A default order on both chains would leave the winner
 * to bean ordering, which no code states. See CHAT-jdsamcia.
 *
 * This chain adds no authentication. A separate issue owns that decision.
 */
@Configuration
@ComponentScan("com.demo.chat.controller.webflux")
@EnableWebFlux
@EnableWebFluxSecurity
class WebFluxSecurity {

    @Bean
    @Order(APPLICATION_CHAIN_ORDER)
    fun filterChain(http: ServerHttpSecurity): SecurityWebFilterChain? = http
        .authorizeExchange {

            it.anyExchange()
                .permitAll()
        }
        .cors { it.disable() }
        .csrf { it.disable() }
        .build()

    companion object {
        /** The application chain runs after every narrower chain. */
        const val APPLICATION_CHAIN_ORDER = Ordered.LOWEST_PRECEDENCE - 100
    }
}