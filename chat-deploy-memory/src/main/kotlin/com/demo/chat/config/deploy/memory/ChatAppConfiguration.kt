package com.demo.chat.config.deploy.memory

import com.demo.chat.domain.*
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.config.EnableWebFlux

@Configuration(proxyBeanMethods = false)
@Import(JacksonAutoConfiguration::class)
@EnableWebFlux
class ChatAppConfiguration {
    @Bean
    fun requestToQueryConverters(): RequestToQueryConverters<IndexSearchRequest> = IndexSearchRequestConverters()
}