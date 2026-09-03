package com.demo.chat.test.vector.simple

import com.demo.chat.config.vector.simple.SimpleVectorStoreConfiguration
import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class SimpleVectorStoreConfigurationTests {

    @Test
    fun `vector simple with embedding mock creates the store bean`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource("test", mapOf("app.service.core.vector" to "simple"))
        )
        context.beanFactory.registerSingleton("embeddingModel", DummyEmbeddingModel())
        context.register(SimpleVectorStoreConfiguration::class.java)
        context.refresh()

        try {
            assertThat(context.getBean(VectorStore::class.java))
                .isInstanceOf(SimpleVectorStore::class.java)
        } finally {
            context.close()
        }
    }

    @Test
    fun `vector selector unset creates no store bean`() {
        val context = AnnotationConfigApplicationContext()
        context.beanFactory.registerSingleton("embeddingModel", DummyEmbeddingModel())
        context.register(SimpleVectorStoreConfiguration::class.java)
        context.refresh()

        try {
            assertThat(context.getBeanNamesForType(VectorStore::class.java)).isEmpty()
        } finally {
            context.close()
        }
    }

    @Test
    fun `simple store stores and recalls message documents`() {
        val store = SimpleVectorStore.builder(DummyEmbeddingModel()).build()
        store.add(
            listOf(
                Document.builder().id("a").text("apple banana")
                    .metadata(mapOf("kind" to "message")).build(),
                Document.builder().id("b").text("zebra stripe")
                    .metadata(mapOf("kind" to "message")).build(),
            )
        )

        val hits = store.similaritySearch(
            SearchRequest.builder().query("apple banana").topK(2).build()
        )

        assertThat(hits).hasSize(2)
        assertThat(hits.first().id).isEqualTo("a")
        assertThat(hits.first().score).isNotNull
    }
}