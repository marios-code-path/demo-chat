package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.DefaultChatJacksonModules
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@TestConfiguration
@Import(DefaultChatJacksonModules::class)
class WebFluxTestConfiguration {
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