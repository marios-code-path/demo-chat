package com.demo.chat.config

import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// The module does not enable the Kotlin all-open compiler plugin. A
// configuration class must be open, like BaseDomainConfiguration.
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["embedding"], havingValue = "mock")
open class MockEmbeddingConfiguration {

    @Bean
    open fun mockEmbeddingModel(): EmbeddingModel = DummyEmbeddingModel()
}