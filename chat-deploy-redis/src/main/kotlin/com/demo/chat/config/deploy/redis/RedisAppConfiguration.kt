package com.demo.chat.config.deploy.redis

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.RequestToQueryConverters
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.config.EnableWebFlux

/**
 * The redis backend's half of the composition, mirroring
 * `ChatAppConfiguration` in chat-deploy-memory and `CassandraAppConfiguration`
 * in chat-deploy-cassandra.
 *
 * The query converter type follows the index, not the persistence: this backend
 * indexes with Lucene, which queries by [IndexSearchRequest], so it takes the
 * same converters memory does. Cassandra differs because its index queries by
 * `Map<String, String>`.
 */
@Configuration(proxyBeanMethods = false)
@Import(JacksonAutoConfiguration::class)
@EnableWebFlux
class RedisAppConfiguration {
    @Bean
    fun requestToQueryConverters(): RequestToQueryConverters<IndexSearchRequest> = IndexSearchRequestConverters()
}
