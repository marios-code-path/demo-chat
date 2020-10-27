package com.demo.chat.test.indexrepo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ElasticContainerConfiguration::class])
class HealthTest {
    @Autowired
    private lateinit var webClient: WebTestClient

    @Test
    fun healthCheck() {
        webClient
                .get()
                .uri("/_cluster/health")
                .exchange()
                .expectStatus().isOk
    }
}