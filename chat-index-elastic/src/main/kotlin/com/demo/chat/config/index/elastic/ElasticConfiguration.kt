package com.demo.chat.config.index.elastic

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient
import org.springframework.data.elasticsearch.client.reactive.ReactiveRestClients
import org.springframework.data.elasticsearch.config.AbstractReactiveElasticsearchConfiguration
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class ElasticConfiguration : AbstractReactiveElasticsearchConfiguration() {

    @Bean
    fun webClient() = WebClient.builder().build()

    // TODO: Configuration Properties Externalization
    override fun reactiveElasticsearchClient(): ReactiveElasticsearchClient =
            ReactiveRestClients.create(
                    ClientConfiguration.builder()
                            .connectedTo("localhost:9200")
                            .withWebClientConfigurer { wc ->
                                val ex = ExchangeStrategies.builder()
                                        .codecs { conf ->
                                            conf.defaultCodecs()
                                                    .maxInMemorySize(-1)
                                        }
                                        .build()
                                return@withWebClientConfigurer wc.mutate().exchangeStrategies(ex).build()
                            }
                            .build()
            )
}