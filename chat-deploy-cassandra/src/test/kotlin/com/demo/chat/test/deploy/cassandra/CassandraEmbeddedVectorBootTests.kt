package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.command.ImportedMessageId
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorIndexJobStore
import com.integrallis.vectors.spring.ai.JavaVectorsVectorStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [ChatApp::class]
)
@TestPropertySource(properties = [
    "spring.config.location=classpath:/application.yml",
    "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
    "spring.application.name=test-cassandra-vector-embedded", "app.server.proto=rsocket",
    "server.port=0", "spring.rsocket.server.port=0", "app.key.type=uuid", "app.nodeid=30",
    "app.users.create=true",
    "app.service.core.key=cassandra", "app.service.core.pubsub=memory",
    "app.service.core.index=lucene", "app.service.core.persistence=cassandra",
    "app.service.core.secrets=cassandra", "app.service.composite", "app.command.bus=memory",
    "app.service.composite.auth=true", "app.service.core.vector=embedded",
    "app.service.core.embedding=mock", "app.controller.recall",
    "app.controller.secrets", "app.controller.key", "app.controller.persistence",
    "app.controller.index", "app.controller.user", "app.controller.message",
    "app.controller.topic", "app.controller.pubsub", "app.service.security.userdetails",
    "management.endpoint.vectorindex.enabled=true",
    "management.endpoints.web.exposure.include=vectorindex"
])
@ActiveProfiles("cassandra-contact-point")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
class CassandraEmbeddedVectorBootTests : CassandraContainerBase() {
    @Autowired private lateinit var context: GenericApplicationContext
    @Value("\${local.server.port}") private var port: Int = 0

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun realEmbedding(registry: DynamicPropertyRegistry) {
            if (System.getProperty("chat.embedding.real") != "true") return
            registry.add("app.service.core.embedding") { "openai" }
            registry.add("app.service.core.embedding.identity") { "nomic-embed-text-v1-5-768" }
            registry.add("app.service.core.embedding.openai.base-url") {
                System.getProperty("chat.embedding.base-url", "http://node2.lan:6090/v1")
            }
            registry.add("app.service.core.embedding.openai.model") { "nomic-embed-text-v1.5.Q8_0.gguf" }
            registry.add("app.service.core.embedding.openai.api-key") { "not-a-secret" }
        }
    }

    @Test
    fun cassandraCompositionActivatesEmbeddedVectors() {
        assertThat(context.containsBean("messageVectorIndexer")).isTrue
        assertThat(context.containsBean("recallService")).isTrue
        assertThat(context.getBean(VectorStore::class.java))
            .isInstanceOf(JavaVectorsVectorStore::class.java)
        if (System.getProperty("chat.embedding.real") == "true") {
            assertThat(context.getBean(EmbeddingModel::class.java).dimensions()).isEqualTo(768)
        }
    }

    @Test
    fun importedMessageSurvivesRebuildAndAppearsInSearch() {
        @Suppress("UNCHECKED_CAST")
        val composite = context.getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<UUID, String>
        @Suppress("UNCHECKED_CAST")
        val roots = context.getBean(RootKeys::class.java) as RootKeys<UUID>
        @Suppress("UNCHECKED_CAST")
        val recall = context.getBean("recallService", MessageRecallService::class.java) as MessageRecallService<UUID>
        @Suppress("UNCHECKED_CAST")
        val reindex = context.getBean(MessageReindexService::class.java) as MessageReindexService<UUID>
        @Suppress("UNCHECKED_CAST")
        val jobs = context.getBean(VectorIndexJobStore::class.java) as VectorIndexJobStore<UUID>

        val sender = composite.userService()
            .addUser(UserCreateRequest("Vector sender", "vectorsender", "http://u"))
            .block(Duration.ofSeconds(10))!!
        val room = composite.topicService()
            .addRoom(ByStringRequest("vectorimportroom"))
            .block(Duration.ofSeconds(10))!!
        val sourceId = "${room.id}:${UUID.randomUUID()}"
        val admin = UsernamePasswordAuthenticationToken(
            ChatUserDetails(User.create(roots.admin(), "Admin", "Admin", "http://u"), listOf("ROLE_ADMIN")),
            "n/a", listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
        )
        composite.messageService()
            .importMessage(MessageImportRequest("bright apple banana", sender.id, room.id, Instant.now(), sourceId))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(admin))
            .block(Duration.ofSeconds(30))!!

        val triggerAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        client.post().uri("/actuator/vectorindex")
            .headers { it.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accepted").isEqualTo(true)
        Flux.interval(Duration.ZERO, Duration.ofMillis(100))
            .filter { !reindex.status().running }
            .next()
            .block(Duration.ofSeconds(60))

        val job = Flux.interval(Duration.ZERO, Duration.ofMillis(100))
            .concatMap {
                jobs.listJobTopics()
                    .filter { JobTopicNames.matches(it.data, 30, "uuid") }
                    .flatMap { jobs.readJobByTopic(it.key) }
                    .filter { !it.startedAt.isBefore(triggerAt) }
                    .collectList()
                    .filter { it.isNotEmpty() }
                    .map { found -> found.maxBy(IndexJob<UUID>::startedAt) }
            }
            .filter { it.outcome != JobOutcome.RUNNING }
            .next()
            .block(Duration.ofSeconds(15))!!
        assertThat(job.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        assertThat(job.failed).isZero()

        val result = recall.recallGlobal(GlobalRecallRequest("bright apple banana", 10, 0.0))
            .block(Duration.ofSeconds(30))!!
        assertThat(result.hits.map { it.key.id })
            .contains(ImportedMessageId.derive(sourceId, room.id))
    }
}
