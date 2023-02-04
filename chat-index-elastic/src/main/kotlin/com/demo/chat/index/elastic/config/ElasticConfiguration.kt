package com.demo.chat.index.elastic.config

import org.apache.http.HttpHost
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchConfiguration

@Configuration
class ElasticConfiguration : ReactiveElasticsearchConfiguration() {

    @Value("\${spring.elasticsearch.uris}")
    private lateinit var address: String

    override fun clientConfiguration(): ClientConfiguration =
        ClientConfiguration.builder()
            .connectedTo(HttpHost.create("https://$address").toHostString())
            .build()
}