package com.demo.chat.config

import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["embedding"], havingValue = "mock")
class MockEmbeddingConfiguration {

    @Bean
    fun mockEmbeddingModel(): EmbeddingModel = DummyEmbeddingModel()
}