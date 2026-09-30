package com.demo.chat.config

import com.demo.chat.config.agent.AgentResourceServerChain
import com.demo.chat.config.agent.AgentSecurityConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.reactive.config.EnableWebFlux

/** The application route chain, after the actuator chain. */
@Configuration
@Import(AgentSecurityConfiguration::class)
@ComponentScan("com.demo.chat.controller.webflux")
@EnableWebFlux
@EnableWebFluxSecurity
class WebFluxSecurity(private val chain: AgentResourceServerChain) {

    /** Every route owned by this chain requires the configured agent scope. */
    @Bean
    @Order(APPLICATION_CHAIN_ORDER)
    fun filterChain(http: ServerHttpSecurity): SecurityWebFilterChain = chain.build(http)

    companion object {
        /** The application chain runs after every narrower chain. */
        const val APPLICATION_CHAIN_ORDER = Ordered.LOWEST_PRECEDENCE - 100
    }
}
