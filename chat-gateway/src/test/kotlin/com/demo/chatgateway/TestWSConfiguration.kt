package com.demo.chatgateway

import com.demo.chatevents.service.TopicServiceMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestWSConfiguration {
    @Bean
    fun topicDistibution() = TopicServiceMemory()

    @Bean
    fun websocketConfiguration() = WebSocketConfigurationProperties(9090)
}