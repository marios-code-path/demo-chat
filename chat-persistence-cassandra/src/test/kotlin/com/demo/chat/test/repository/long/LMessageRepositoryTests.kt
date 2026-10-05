package com.demo.chat.test.repository.long

import com.demo.chat.persistence.cassandra.domain.ChatMessageById
import com.demo.chat.persistence.cassandra.domain.ChatMessageByIdKey
import com.demo.chat.persistence.cassandra.repository.ChatMessageRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A `Long` message id must write to and read from `chat_message_id`. Every
 * other message repository test uses `UUID` keys, so no test wrote a `Long`
 * id. See `CHAT-xcmpudyb`.
 */
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long"])
@Tag("integration")
class LMessageRepositoryTests : CassandraSchemaTest<Long>(TestLongKeyGenerator()) {

    @Autowired
    lateinit var repo: ChatMessageRepository<Long>

    private fun row(id: Long, time: Instant) = ChatMessageById(
        ChatMessageByIdKey(id, keyGenerator.nextId(), keyGenerator.nextId(), time),
        "long message",
        true
    )

    @Test
    fun `should save a Long message id and find it by that id`() {
        val time = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val stored = row(keyGenerator.nextId(), time)

        StepVerifier
            .create(repo.add(stored).then(repo.findByKeyId(stored.key.id)))
            .assertNext { assertThat(it).isEqualTo(stored) }
            .verifyComplete()
    }

    @Test
    fun `should hide a Long message after a removal by key`() {
        val stored = row(keyGenerator.nextId(), Instant.now().truncatedTo(ChronoUnit.MILLIS))

        StepVerifier
            .create(
                repo.add(stored)
                    .then(repo.rem(TestKeys.key(stored.key.id)))
                    .then(repo.findByKeyId(stored.key.id))
            )
            .assertNext { assertThat(it.record).isFalse() }
            .verifyComplete()
    }
}
