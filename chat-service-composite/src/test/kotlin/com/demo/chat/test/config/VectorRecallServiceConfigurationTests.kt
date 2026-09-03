package com.demo.chat.test.config

import com.demo.chat.config.service.composite.VectorRecallServiceConfiguration
import com.demo.chat.domain.LongUtil
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class VectorRecallServiceConfigurationTests {

    @Test
    fun `both selectors unset leaves recall inactive`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource("test", mapOf("app.service.composite" to "true"))
        )
        context.beanFactory.registerSingleton("typeUtil", LongUtil())
        context.beanFactory.registerSingleton("vectorStore", MockVectorStore())
        context.register(VectorRecallServiceConfiguration::class.java)
        context.refresh()

        try {
            Assertions
                .assertThat(context.getBeanNamesForType(MessageVectorIndexer::class.java))
                .isEmpty()
        } finally {
            context.close()
        }
    }

    @Test
    fun `both selectors set creates the indexer bean`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "test",
                mapOf(
                    "app.service.composite" to "true",
                    "app.service.core.vector" to "simple",
                    "app.service.core.embedding" to "mock",
                    "app.key.type" to "long",
                )
            )
        )
        context.beanFactory.registerSingleton("typeUtil", LongUtil())
        context.beanFactory.registerSingleton("vectorStore", MockVectorStore())
        context.register(VectorRecallServiceConfiguration::class.java)
        context.refresh()

        try {
            Assertions
                .assertThat(context.getBean(MessageVectorIndexer::class.java))
                .isNotNull
        } finally {
            context.close()
        }
    }

    @Test
    fun `one selector set creates no indexer bean`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "test",
                mapOf(
                    "app.service.composite" to "true",
                    "app.service.core.vector" to "simple",
                )
            )
        )
        context.beanFactory.registerSingleton("typeUtil", LongUtil())
        context.beanFactory.registerSingleton("vectorStore", MockVectorStore())
        context.register(VectorRecallServiceConfiguration::class.java)
        context.refresh()

        try {
            Assertions
                .assertThat(context.getBeanNamesForType(MessageVectorIndexer::class.java))
                .isEmpty()
        } finally {
            context.close()
        }
    }
}
