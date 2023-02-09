package com.demo.chat.test.indexrepo

import co.elastic.clients.elasticsearch._types.HealthStatus
import co.elastic.clients.elasticsearch._types.Level
import co.elastic.clients.elasticsearch.cluster.HealthResponse
import com.demo.chat.config.index.elastic.ElasticConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchClient
import reactor.test.StepVerifier

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ElasticConfiguration::class, ElasticContainerBase.ConfConfig::class]
)
class ElasticHealthTest : ElasticContainerBase() {

    @Autowired
    private lateinit var client: ReactiveElasticsearchClient

    @Test
    fun healthCheck() {
        StepVerifier
            .create(client.cluster().health { builder ->
                builder.level(Level.Cluster)
            })
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isInstanceOf(HealthResponse::class.java)
                    .extracting(HealthResponse::status)
                    .isNotEqualTo(HealthStatus.Red)
            }
            .verifyComplete()
    }
}