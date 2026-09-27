package com.demo.chat.test.repository

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.test.key.TestKeys

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.index.cassandra.impl.MessageIndex
import com.demo.chat.index.cassandra.repository.ChatMessageByTopicRepository
import com.demo.chat.index.cassandra.repository.ChatMessageByUserRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.IndexRepositoryTestConfiguration
import com.demo.chat.test.TestUUIDKeyGenerator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID
import java.util.function.Function

/**
 * A topic read through the production index service and the production
 * converter.
 *
 * The repository test beside this one writes rows and reads them back through
 * the repository. It never meets the index service or the converter, so it
 * cannot see a query that names the wrong field. `MessageIndex.findBy` branches
 * on `query.keys.first()`, and a map that leads with another key falls to the
 * empty branch.
 */
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [IndexRepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class MessageTopicQueryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {

    @Autowired
    lateinit var byTopicRepo: ChatMessageByTopicRepository<UUID>

    @Autowired
    lateinit var byUserRepo: ChatMessageByUserRepository<UUID>

    private val converters = MapRequestConverters()

    private fun index() = MessageIndex(
        Function<String, UUID> { text -> UUID.fromString(text) },
        byUserRepo,
        byTopicRepo,
        FakeKeyServices.uuidRoots(),
    )

    @Test
    fun `a topic query returns only the messages of that topic`() {
        val index = index()
        val topic = UUID.randomUUID()
        val otherTopic = UUID.randomUUID()
        val user = UUID.randomUUID()
        // msg_id is a TIMEUUID column, so a message identifier must be time
        // based. A version 4 uuid is rejected by the schema.
        val first = Uuids.timeBased()
        val second = Uuids.timeBased()
        val other = Uuids.timeBased()

        index.add(Message.create(TestKeys.message(first, user, topic), "apple", true)).block()
        index.add(Message.create(TestKeys.message(second, user, topic), "banana", true)).block()
        index.add(Message.create(TestKeys.message(other, user, otherTopic), "cherry", true)).block()

        val found = index
            .findBy(converters.topicIdToQuery(ByIdRequest(topic)))
            .collectList()
            .block()!!

        Assertions.assertThat(found.map { it.id }).containsExactlyInAnyOrder(first, second)
    }

    // The topic and the user are ordinary uuid columns, so a random value is
    // correct for them.
    @Test
    fun `a topic with no message returns nothing`() {
        val index = index()
        val topic = UUID.randomUUID()
        val user = UUID.randomUUID()

        index.add(Message.create(TestKeys.message(Uuids.timeBased(), user, topic), "apple", true)).block()

        val found = index
            .findBy(converters.topicIdToQuery(ByIdRequest(UUID.randomUUID())))
            .collectList()
            .block()!!

        Assertions.assertThat(found).isEmpty()
    }
}
