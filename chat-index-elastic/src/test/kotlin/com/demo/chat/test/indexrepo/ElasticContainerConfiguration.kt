package com.demo.chat.test.indexrepo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchClient
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchTemplate
import org.springframework.data.elasticsearch.config.ElasticsearchConfigurationSupport
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.elasticsearch.ElasticsearchContainer


@AutoConfigureOrder
@Configuration
@EnableReactiveElasticsearchRepositories(basePackages = ["com.demo.chat"])
class ElasticContainerConfiguration {
    val log = LoggerFactory.getLogger(this::class.qualifiedName)

    @Value("\${embedded.elastic.image:docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2-amd64}")
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
    fun reactiveElasticsearchTemplate(client: ReactiveElasticsearchClient,
                                      converter: ElasticsearchConverter
    ): ReactiveElasticsearchTemplate =
            ReactiveElasticsearchTemplate(client, converter)

    @Bean
    @DependsOn("embeddedElastic")
    fun webClient(container: ElasticsearchContainer): WebTestClient =
            WebTestClient
                    .bindToServer()
                    .baseUrl("http://${container.httpHostAddress}")
                    .build()

    @Bean
    @DependsOn("embeddedElastic")
    fun client(container: ElasticsearchContainer,
               config: ClientConfiguration): ReactiveElasticsearchClient =
            ElasticsearchClients
                    .createReactive(config)

    @Bean
    @DependsOn("embeddedElastic")
    fun clientConfiguration(container: ElasticsearchContainer) : ClientConfiguration =
        ClientConfiguration.builder()
            .connectedTo(container.httpHostAddress)
            .build()

    @org.springframework.context.annotation.Configuration
    class Configuration : ElasticsearchConfigurationSupport ()
}