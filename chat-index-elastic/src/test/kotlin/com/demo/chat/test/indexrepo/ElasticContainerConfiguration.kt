package com.demo.chat.test.indexrepo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient
import org.springframework.data.elasticsearch.client.reactive.ReactiveRestClients
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchTemplate
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.testcontainers.elasticsearch.ElasticsearchContainer


@AutoConfigureOrder
@Configuration
@EnableReactiveElasticsearchRepositories(basePackages = ["com.demo.chat"])
class ElasticContainerConfiguration {
    val log = LoggerFactory.getLogger(this::class.qualifiedName)

    @Value("\${embedded.elastic.image}")
    private lateinit var imageName: String

    @Bean(name = ["embeddedElastic"], destroyMethod = "stop")
    fun elasticContainer(environment: ConfigurableEnvironment): ElasticsearchContainer =
            ElasticsearchContainer(imageName)
                    .apply {
                        log.info("Elasticsearch Container is Starting.")
                        start()
                        log.info("Elasticsearch is Listening: ${this.httpHostAddress}")
                    }

    @Bean
    @DependsOn("embeddedElastic")
    fun reactiveElasticsearchTemplate(client: ReactiveElasticsearchClient): ReactiveElasticsearchTemplate =
            ReactiveElasticsearchTemplate(client)

    @Bean
    @DependsOn("embeddedElastic")
    fun webClient(container: ElasticsearchContainer): WebTestClient =
            WebTestClient
                    .bindToServer()
                    .baseUrl("http://${container.httpHostAddress}")
                    .build()

    @Bean
    @DependsOn("embeddedElastic")
    fun client(container: ElasticsearchContainer): ReactiveElasticsearchClient =
            ReactiveRestClients
                    .create(
                            ClientConfiguration.builder()
                                    .connectedTo(container.httpHostAddress)
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