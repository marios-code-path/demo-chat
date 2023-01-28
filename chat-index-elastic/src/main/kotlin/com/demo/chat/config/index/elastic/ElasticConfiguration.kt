package com.demo.chat.config.index.elastic

import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchConfiguration

@Configuration
class ElasticConfiguration : ReactiveElasticsearchConfiguration() {

    // TODO: Configuration Properties Externalization
    override fun clientConfiguration(): ClientConfiguration = ClientConfiguration.builder()
        .connectedTo("localhost:9200")
//                            .withClientConfigurer { wc ->
//                                val ex = ExchangeStrategies.builder()
//                                        .codecs { conf ->
//                                            conf.defaultCodecs()
//                                                    .maxInMemorySize(-1)
//                                        }
//                                        .build()
//                                return@withWebClientConfigurer wc.mutate().exchangeStrategies(ex).build()
//                            }
        .build()
}