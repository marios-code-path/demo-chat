package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.cassandra.impl.AuthMetaPersistenceCassandra
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.persistence.cassandra.impl.KeyValuePersistenceCassandra
import com.demo.chat.persistence.cassandra.impl.MembershipPersistenceCassandra
import com.demo.chat.persistence.cassandra.impl.MessagePersistenceCassandra
import com.demo.chat.persistence.cassandra.impl.TopicPersistenceCassandra
import com.demo.chat.persistence.cassandra.impl.UserPersistenceCassandra
import com.demo.chat.persistence.cassandra.repository.AuthMetadataRepository
import com.demo.chat.persistence.cassandra.repository.ChatMessageRepository
import com.demo.chat.persistence.cassandra.repository.ChatUserRepository
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.persistence.cassandra.repository.TopicMembershipRepository
import com.demo.chat.persistence.cassandra.repository.TopicRepository
import com.demo.chat.service.core.IKeyGenerator
import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.persistence.StoreDomainTestBase
import com.demo.chat.test.repository.RepositoryTestConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// The Cassandra stores refuse a key of another domain. See CHAT-avduuqwp, T5.

private val ROOTS = FakeKeyServices.uuidRoots()

// The message table keys on a TIMEUUID, which takes only a time-based UUID.
private val TIME_BASED = object : IKeyGenerator<UUID> {
    override fun nextId(): UUID = Uuids.timeBased()
}

private fun keys(template: ReactiveCassandraTemplate) = KeyServiceCassandra(template, TIME_BASED, ROOTS)

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraUserStoreDomainTests @Autowired constructor(template: ReactiveCassandraTemplate, repo: ChatUserRepository<UUID>) :
    StoreDomainTestBase<UUID, User<UUID>>(
        UserPersistenceCassandra(keys(template), ROOTS, repo), keys(template), ROOTS, ChatDomain.USER,
        { User.create(it, "n", "h-${it.id}", "http://u") }, { it.key },
    )

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraTopicStoreDomainTests @Autowired constructor(template: ReactiveCassandraTemplate, repo: TopicRepository<UUID>) :
    StoreDomainTestBase<UUID, MessageTopic<UUID>>(
        TopicPersistenceCassandra(keys(template), ROOTS, repo), keys(template), ROOTS, ChatDomain.MESSAGE_TOPIC,
        { MessageTopic.create(it, "room-${it.id}") }, { it.key },
    )

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraMessageStoreDomainTests @Autowired constructor(template: ReactiveCassandraTemplate, repo: ChatMessageRepository<UUID>) :
    StoreDomainTestBase<UUID, Message<UUID, String>>(
        MessagePersistenceCassandra(keys(template), ROOTS, repo), keys(template), ROOTS, ChatDomain.MESSAGE,
        { Message.create(MessageKey.of(it.id, it.root, UUID.randomUUID(), UUID.randomUUID()), "m", true) },
        { Key.of(it.key.id, it.key.root) },
    )

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraMembershipStoreDomainTests @Autowired constructor(template: ReactiveCassandraTemplate, repo: TopicMembershipRepository<UUID>) :
    StoreDomainTestBase<UUID, TopicMembership<UUID>>(
        MembershipPersistenceCassandra(keys(template), ROOTS, repo), keys(template), ROOTS, ChatDomain.TOPIC_MEMBERSHIP,
        { TopicMembership.create(it.id, UUID.randomUUID(), UUID.randomUUID()) },
        { Key.of(it.key, ROOTS.of(ChatDomain.TOPIC_MEMBERSHIP).id) },
    )

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraAuthMetaStoreDomainTests @Autowired constructor(template: ReactiveCassandraTemplate, repo: AuthMetadataRepository<UUID>) :
    StoreDomainTestBase<UUID, AuthMetadata<UUID>>(
        AuthMetaPersistenceCassandra(keys(template), ROOTS, repo), keys(template), ROOTS, ChatDomain.AUTH_METADATA,
        { AuthMetadata.create(it, ROOTS.anon(), ROOTS.of(ChatDomain.USER), "GET", false, Long.MAX_VALUE) }, { it.key },
    )

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraKeyValueStoreDomainTests @Autowired constructor(
    template: ReactiveCassandraTemplate, repo: KeyValuePairRepository<UUID>, mapper: ObjectMapper,
) : StoreDomainTestBase<UUID, KeyValuePair<UUID, Any>>(
    KeyValuePersistenceCassandra(keys(template), ROOTS, repo, mapper), keys(template), ROOTS, ChatDomain.KEY_VALUE_PAIR,
    { KeyValuePair.create(it, "v" as Any) }, { it.key },
)

/**
 * The message store keeps the supplied id, sender, destination and timestamp,
 * so a stored message reads back by its own key. See `CHAT-avduuqwp`, E20.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraMessageIdentityTests @Autowired constructor(
    private val template: ReactiveCassandraTemplate,
    private val repo: ChatMessageRepository<UUID>,
) {
    @Test
    fun `a stored message reads back by its supplied key with every identity field`() {
        val registry = keys(template)
        val store = MessagePersistenceCassandra(registry, ROOTS, repo)
        val minted = store.key().block()!!
        val sender = registry.key(ChatDomain.USER).block()!!
        val room = registry.key(ChatDomain.MESSAGE_TOPIC).block()!!
        // Cassandra keeps milliseconds, so the supplied timestamp carries no finer part.
        val sentAt = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.MILLIS)

        store.add(Message.create(SimpleMessageKey(minted.id, minted.root, sender.id, room.id, sentAt), "hello", true)).block()

        val read = store.get(minted).block()!!
        assertThat(setOf(minted.id, sender.id, room.id)).hasSize(3)
        assertThat(read.key.id).isEqualTo(minted.id)
        assertThat(read.key.root).isEqualTo(ROOTS.of(ChatDomain.MESSAGE).id)
        assertThat(read.key.from).isEqualTo(sender.id)
        assertThat(read.key.dest).isEqualTo(room.id)
        assertThat(read.key.timestamp).isEqualTo(sentAt)
        assertThat(read.data).isEqualTo("hello")
        assertThat(registry.rootOf(minted.id).block()).isEqualTo(ROOTS.of(ChatDomain.MESSAGE).id)
    }
}
