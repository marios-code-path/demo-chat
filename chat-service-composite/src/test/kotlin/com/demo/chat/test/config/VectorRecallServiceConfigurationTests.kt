package com.demo.chat.test.config

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.config.service.composite.VectorRecallServiceConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import com.demo.chat.domain.LongUtil
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorCoveragePolicy
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import com.demo.chat.test.vector.MockVectorStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import reactor.core.publisher.Flux
import java.time.Duration

class VectorRecallServiceConfigurationTests {

    private val allProperties = mapOf(
        "app.service.composite" to "true",
        "app.service.core.vector" to "simple",
        "app.service.core.embedding" to "mock",
        "app.key.type" to "long",
        "app.nodeid" to "1",
    )

    /**
     * Registers every bean the configuration reads, and nothing else.
     *
     * The three provider interfaces come from chat-core, so this module can
     * implement them. InMemoryServiceBeans backs them with the doubles in
     * VectorTestFakes.
     */
    private val beans = InMemoryServiceBeans()

    private fun contextWith(properties: Map<String, String>): AnnotationConfigApplicationContext {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(MapPropertySource("test", properties))
        context.beanFactory.registerSingleton("typeUtil", LongUtil())
        context.beanFactory.registerSingleton("vectorStore", MockVectorStore())
        context.beanFactory.registerSingleton("embeddingIdentity", EmbeddingIdentity.MOCK)
        context.beanFactory.registerSingleton("persistenceBeans", beans.persistence())
        context.beanFactory.registerSingleton("indexBeans", beans.index())
        context.beanFactory.registerSingleton("pubSubBeans", beans.pubSub())
        // A deployment builds this mapper from the Module beans that
        // JacksonModules declares. The registration repeats that result.
        context.beanFactory.registerSingleton("codecMapper", chatMapper())
        context.register(VectorRecallServiceConfiguration::class.java)
        context.refresh()
        return context
    }

    private fun chatMapper(): ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }

    private fun publishReady(context: AnnotationConfigApplicationContext) {
        context.publishEvent(
            ApplicationReadyEvent(SpringApplication(), arrayOf(), context, Duration.ZERO)
        )
    }

    private fun jobCount(context: AnnotationConfigApplicationContext): Long =
        context.getBean(VectorIndexJobStore::class.java)
            .listJobTopics()
            .count()
            .block(Duration.ofSeconds(10))!!

    private fun awaitNotRunning(context: AnnotationConfigApplicationContext) {
        val reindex = context.getBean(MessageReindexService::class.java)
        Flux.interval(Duration.ZERO, Duration.ofMillis(20))
            .map { reindex.status() }
            .filter { status -> !status.running }
            .next()
            .block(Duration.ofSeconds(30))
    }

    @Test
    fun `every vector bean exists with the composite and both selectors`() {
        val context = contextWith(allProperties)

        try {
            Assertions.assertThat(context.getBean(VectorIndexState::class.java)).isNotNull
            Assertions.assertThat(context.getBean(VectorIndexJobStore::class.java)).isNotNull
            Assertions.assertThat(context.getBean(VectorCoveragePolicy::class.java)).isNotNull
            Assertions.assertThat(context.getBean(MessageVectorIndexer::class.java)).isNotNull
            Assertions.assertThat(context.getBean(MessageRecallService::class.java)).isNotNull
            Assertions.assertThat(context.getBean(MessageReindexService::class.java)).isNotNull
        } finally {
            context.close()
        }
    }

    @Test
    fun `both selectors unset leaves recall inactive`() {
        val context = contextWith(mapOf("app.service.composite" to "true"))

        try {
            Assertions
                .assertThat(context.getBeanNamesForType(MessageVectorIndexer::class.java))
                .isEmpty()
            Assertions
                .assertThat(context.getBeanNamesForType(VectorIndexJobStore::class.java))
                .isEmpty()
        } finally {
            context.close()
        }
    }

    @Test
    fun `one selector set creates no recall bean`() {
        val context = contextWith(
            mapOf(
                "app.service.composite" to "true",
                "app.service.core.vector" to "simple",
                "app.key.type" to "long",
                "app.nodeid" to "1",
            )
        )

        try {
            Assertions.assertThat(context.getBeanNamesForType(MessageRecallService::class.java)).isEmpty()
            Assertions.assertThat(context.getBeanNamesForType(VectorIndexJobStore::class.java)).isEmpty()
        } finally {
            context.close()
        }
    }

    // The default must start no rebuild. Real embedding throughput is still
    // unmeasured, so an automatic rebuild could delay readiness or send
    // uncontrolled external requests.
    @Test
    fun `the default startup action starts no job`() {
        val context = contextWith(allProperties)

        try {
            publishReady(context)

            Assertions.assertThat(jobCount(context)).isEqualTo(0L)
        } finally {
            context.close()
        }
    }

    // AnnotationConfigApplicationContext refreshes. It never publishes
    // ApplicationReadyEvent, so a listener bound to that event fires only when
    // the test publishes it.
    @Test
    fun `the rebuild startup action starts exactly one job`() {
        val context = contextWith(allProperties + ("app.vector.index.startup" to "rebuild"))

        try {
            publishReady(context)
            awaitNotRunning(context)

            Assertions.assertThat(jobCount(context)).isEqualTo(1L)
        } finally {
            context.close()
        }
    }

    @Test
    fun `an unknown trust value fails the context`() {
        Assertions
            .assertThatThrownBy {
                contextWith(allProperties + ("app.vector.index.trust" to "maybe")).close()
            }
            .hasMessageContaining("app.vector.index.trust")
    }

    @Test
    fun `an unknown startup value fails the context`() {
        Assertions
            .assertThatThrownBy {
                contextWith(allProperties + ("app.vector.index.startup" to "maybe")).close()
            }
            .hasMessageContaining("app.vector.index.startup")
    }
}
