package com.demo.chat.test.repository.uuid

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.persistence.cassandra.domain.ChatTopic
import com.demo.chat.persistence.cassandra.domain.ChatTopicKey
import com.demo.chat.persistence.cassandra.repository.TopicRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class TopicRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {

    private val ROOMNAME = "XYZ"

    @Autowired
    lateinit var repo: TopicRepository<UUID>

    @Test
    fun `inactive rooms dont appear`() {
        val roomId = keyGenerator.nextId()
        val room = ChatTopic(ChatTopicKey(roomId), ROOMNAME, true)

        val saveFlux = repo.add(room)

        val deleteMono = repo.rem(TestKeys.key(roomId))

        val findActiveRooms =
            repo.findAll()
                .filter {
                    it.active
                }

        val composed = Flux.from(saveFlux)
            .then(deleteMono)
            .thenMany(findActiveRooms)

        StepVerifier
            .create(composed)
            .expectSubscription()
            .expectNextCount(0)
            .verifyComplete()
    }

    @Test
    fun `should fail to find room`() {
        val queryFlux = repo
            .findByKeyId(keyGenerator.nextId())
            .switchIfEmpty (Mono.error(TestBase.NotFoundException()))


        StepVerifier
            .create(queryFlux)
            .expectSubscription()
            .expectError()
            .verify()
    }

    @Test
    fun `should save many and find as list`() {
        StepVerifier
            .create(
                Flux.just(
                    ChatTopicKey(keyGenerator.nextId()),
                    ChatTopicKey(keyGenerator.nextId())
                )
                    .map {
                        ChatTopic(it, TestBase.randomAlphaNumeric(6), true)
                    }
                    .flatMap {
                        repo.add(it)
                    }
                    .thenMany(repo.findAll())
            )
            .expectSubscription()
            .assertNext(::rowAssertions)
            .assertNext(::rowAssertions)
            .verifyComplete()
    }

    /** A row of `chat_room` holds an id and a name. See `CHAT-avduuqwp`. */
    private fun rowAssertions(row: ChatTopic<UUID>) {
        org.assertj.core.api.Assertions.assertThat(row.key.id).isNotNull
        org.assertj.core.api.Assertions.assertThat(row.data).isNotBlank
    }
}
