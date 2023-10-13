package com.demo.chat.config.deploy.memory

import com.demo.chat.domain.*
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration(proxyBeanMethods = false)
@Import(JacksonAutoConfiguration::class)
class ChatAppConfiguration {
    @Bean
    fun requestToQueryConverters(): RequestToQueryConverters<IndexSearchRequest> = IndexSearchRequestConverters()
}