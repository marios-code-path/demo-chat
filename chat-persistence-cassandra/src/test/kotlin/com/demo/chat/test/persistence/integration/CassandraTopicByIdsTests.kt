package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.persistence.cassandra.impl.TopicPersistenceCassandra
import com.demo.chat.persistence.cassandra.repository.TopicRepository
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.repository.RepositoryTestConfiguration
import com.datastax.oss.driver.api.core.uuid.Uuids
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID

private val UUID_TIME_BASED = object : IKeyGenerator<UUID> {
    override fun nextId(): UUID = Uuids.timeBased()
}

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=long"])
@Tag("integration")
class CassandraLongTopicByIdsTests : CassandraSchemaTest<Long>(TestLongKeyGenerator()) {
    private val roots = FakeKeyServices.longRoots()

    @Test
    fun `a room store reads multiple rooms by key`() {
        val keys = KeyServiceCassandra(template, TestLongKeyGenerator(), roots)
        val store = TopicPersistenceCassandra(keys, roots, repo)
        val first = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        val second = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        store.add(MessageTopic.create(first, "first room")).block()
        store.add(MessageTopic.create(second, "second room")).block()

        val rooms = store.byIds(listOf(first, second)).collectList().block()!!

        assertThat(rooms.map { it.data }).containsExactlyInAnyOrder("first room", "second room")
    }

    @Test
    fun `a room store skips keys with no matching room`() {
        val keys = KeyServiceCassandra(template, TestLongKeyGenerator(), roots)
        val store = TopicPersistenceCassandra(keys, roots, repo)
        val stored = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        val missing = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        store.add(MessageTopic.create(stored, "stored room")).block()

        val rooms = store.byIds(listOf(stored, missing)).collectList().block()!!

        assertThat(rooms.map { it.key.id }).containsExactly(stored.id)
        assertThat(rooms.single().data).isEqualTo("stored room")
    }

    @Test
    fun `an empty key list returns no rooms`() {
        val keys = KeyServiceCassandra(template, TestLongKeyGenerator(), roots)
        val store = TopicPersistenceCassandra(keys, roots, repo)

        assertThat(store.byIds(emptyList()).collectList().block()).isEmpty()
    }

    @Autowired
    lateinit var repo: TopicRepository<Long>
}

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraUuidTopicByIdsTests : CassandraSchemaTest<UUID>(UUID_TIME_BASED) {
    private val roots = FakeKeyServices.uuidRoots()

    @Test
    fun `a room store reads multiple rooms by key`() {
        val keys = KeyServiceCassandra(template, UUID_TIME_BASED, roots)
        val store = TopicPersistenceCassandra(keys, roots, repo)
        val first = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        val second = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        store.add(MessageTopic.create(first, "first room")).block()
        store.add(MessageTopic.create(second, "second room")).block()

        val rooms = store.byIds(listOf(first, second)).collectList().block()!!

        assertThat(rooms.map { it.data }).containsExactlyInAnyOrder("first room", "second room")
    }

    @Test
    fun `a room store skips keys with no matching room`() {
        val keys = KeyServiceCassandra(template, UUID_TIME_BASED, roots)
        val store = TopicPersistenceCassandra(keys, roots, repo)
        val stored = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        val missing = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        store.add(MessageTopic.create(stored, "stored room")).block()

        val rooms = store.byIds(listOf(stored, missing)).collectList().block()!!

        assertThat(rooms.map { it.key.id }).containsExactly(stored.id)
        assertThat(rooms.single().data).isEqualTo("stored room")
    }

    @Test
    fun `an empty key list returns no rooms`() {
        val keys = KeyServiceCassandra(template, UUID_TIME_BASED, roots)
        val store = TopicPersistenceCassandra(keys, roots, repo)

        assertThat(store.byIds(emptyList()).collectList().block()).isEmpty()
    }

    @Autowired
    lateinit var repo: TopicRepository<UUID>
}
