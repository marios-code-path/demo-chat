package com.demo.chat.config.deploy.kafka

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.RequestToQueryConverters
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.config.EnableWebFlux

/**
 * Kafka deployment app configuration.
 *
 * Mirrors [com.demo.chat.config.deploy.memory.ChatAppConfiguration]: this
 * backend pairs Kafka messaging with memory persistence and the Lucene index,
 * so its query type is [IndexSearchRequest], not the Cassandra map form.
 */
@Configuration(proxyBeanMethods = false)
@Import(JacksonAutoConfiguration::class)
@EnableWebFlux
class KafkaAppConfiguration {
    @Bean
    fun requestToQueryConverters(): RequestToQueryConverters<IndexSearchRequest> =
        IndexSearchRequestConverters()
}
