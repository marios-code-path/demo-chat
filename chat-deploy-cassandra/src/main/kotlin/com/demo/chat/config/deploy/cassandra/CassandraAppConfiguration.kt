package com.demo.chat.config.deploy.cassandra

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.RequestToQueryConverters
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.springframework.web.reactive.config.EnableWebFlux

@Configuration(proxyBeanMethods = false)
@Import(JacksonAutoConfiguration::class)
@EnableReactiveCassandraRepositories(
    basePackages = [
        "com.demo.chat.persistence.cassandra",
        "com.demo.chat.index.cassandra"
    ]
)
@EnableConfigurationProperties(CassandraProperties::class)
@ComponentScan("com.demo.chat.config.deploy.cassandra.dse")
@EnableWebFlux
class CassandraAppConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    fun requestToQueryConverters(): RequestToQueryConverters<Map<String, String>> = MapRequestConverters()

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene")
    fun luceneRequestToQueryConverters(): RequestToQueryConverters<IndexSearchRequest> = IndexSearchRequestConverters()
}
