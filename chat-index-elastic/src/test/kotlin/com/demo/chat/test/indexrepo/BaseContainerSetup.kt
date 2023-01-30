package com.demo.chat.test.indexrepo

import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
open class BaseContainerSetup {

    @TestConfiguration
    class ConfConfig {
        @Bean
        fun restClient(@Value("\${spring.elasticsearch.uris}") host: String): RestClient {
            val host = HttpHost.create("https://$host")
            val credentialsProvider = BasicCredentialsProvider().apply {
                setCredentials(AuthScope.ANY, UsernamePasswordCredentials("elastic", "s3cret"))
            }
            val builder = RestClient.builder(host)

            builder.setHttpClientConfigCallback { clientBuilder ->
                clientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                clientBuilder.setSSLContext(sslContext())
                clientBuilder
            }
            return builder.build()
        }
    }

    companion object {
        val imageName = "docker.elastic.co/elasticsearch/elasticsearch:8.6.0"

        @Container
        val elasticContainer = ElasticsearchContainer(imageName).apply {
                withExposedPorts(9200)
                withPassword("s3cret")
                start()

                val host = HttpHost("localhost", getMappedPort(9200), "https")
                val credentialsProvider = BasicCredentialsProvider().apply {
                    setCredentials(AuthScope.ANY, UsernamePasswordCredentials("elastic", "s3cret"))
                }
                val builder = RestClient.builder(host)

                builder.setHttpClientConfigCallback { clientBuilder ->
                    clientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                    clientBuilder.setSSLContext(this.createSslContextFromCa())
                    clientBuilder
                }
            }

        fun sslContext() = elasticContainer.createSslContextFromCa()

        @JvmStatic
        @DynamicPropertySource
        fun elasticProperties(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("spring.elasticsearch.uris") { elasticContainer.httpHostAddress }
            registry.add("spring.elasticsearchport") { elasticContainer.getMappedPort(9200) }
            registry.add("spring.elasticsearch.username") { "elastic" }
            registry.add("spring.elasticsearch.password") { "s3cret" }

        }
    }
}
