package com.demo.chat.test.repository

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.domain.Key
import com.demo.chat.domain.cassandra.ChatTopicName
import com.demo.chat.domain.cassandra.ChatTopicNameKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [CassandraTestConfiguration::class,
UUIDKeyConfiguration::class])
@TestPropertySource(properties = ["embedded.cassandra.enabled=false"])
class TopicIndexRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {

    @Autowired
    lateinit var byNameRepo: TopicByNameRepository<UUID>

    @Test
    fun `should save and find by name`() {
        val saveFlux = Flux.just(Key.funKey(Uuids.timeBased()))
            .flatMap {
                byNameRepo.save(ChatTopicName(ChatTopicNameKey(it.id, "XYZ"), true))
            }

        val findFlux = byNameRepo
            .findByKeyName("XYZ")

        val composed = Flux
            .from(saveFlux)
            .thenMany(findFlux)

        StepVerifier
            .create(composed)
            .assertNext {
                TestBase.topicAssertions(it)
            }
            .verifyComplete()
    }
}