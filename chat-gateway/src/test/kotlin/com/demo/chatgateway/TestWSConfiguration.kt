package com.demo.chatgateway

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestWSConfiguration {

    @Bean
    fun websocketConfiguration() = WebSocketConfigurationProperties(9090)
}