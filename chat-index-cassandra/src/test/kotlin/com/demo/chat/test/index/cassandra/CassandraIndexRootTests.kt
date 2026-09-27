package com.demo.chat.test.index.cassandra

import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndex
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexKey
import com.demo.chat.index.cassandra.domain.ChatMessageByTopic
import com.demo.chat.index.cassandra.domain.ChatMessageByTopicKey
import com.demo.chat.index.cassandra.domain.ChatTopicName
import com.demo.chat.index.cassandra.domain.ChatTopicNameKey
import com.demo.chat.index.cassandra.domain.ChatUserHandle
import com.demo.chat.index.cassandra.domain.ChatUserHandleKey
import com.demo.chat.index.cassandra.domain.TopicMembershipByMember
import com.demo.chat.index.cassandra.impl.AuthMetadataIndex
import com.demo.chat.index.cassandra.impl.KeyValueIndex
import com.demo.chat.index.cassandra.impl.MembershipIndex
import com.demo.chat.index.cassandra.impl.MessageIndex
import com.demo.chat.index.cassandra.impl.TopicIndex
import com.demo.chat.index.cassandra.impl.UserIndex
import com.demo.chat.index.cassandra.repository.*
import com.demo.chat.service.core.KeyValueIndexFields
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TypedKeyValueIndexFields
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Each Cassandra index answers keys under the root of its own domain. The
 * repositories are mocks that answer one row. See `CHAT-avduuqwp`.
 */
@Suppress("UNCHECKED_CAST")
class CassandraIndexRootTests {

    private val roots = FakeKeyServices.longRoots()

    private fun root(domain: ChatDomain) = roots.of(domain).id

    @Test
    fun `the user index answers the USER root`() {
        val repo = mock(ChatUserHandleRepository::class.java) as ChatUserHandleRepository<Long>
        given(repo.findByKeyHandle("h")).willReturn(Mono.just(ChatUserHandle(ChatUserHandleKey(11L, "h"), "n", "u", Instant.EPOCH)))

        val key = UserIndex(repo, roots).findBy(mapOf(UserIndexService.HANDLE to "h")).blockFirst()!!
        assertThat(key.id).isEqualTo(11L)
        assertThat(key.root).isEqualTo(root(ChatDomain.USER))
    }

    @Test
    fun `the topic index answers the MESSAGE_TOPIC root`() {
        val repo = mock(TopicByNameRepository::class.java) as TopicByNameRepository<Long>
        given(repo.findByKeyName("room")).willReturn(Mono.just(ChatTopicName(ChatTopicNameKey(12L, "room"), true)))

        val key = TopicIndex(repo, roots).findBy(mapOf(TopicIndexService.NAME to "room")).blockFirst()!!
        assertThat(key.root).isEqualTo(root(ChatDomain.MESSAGE_TOPIC))
    }

    @Test
    fun `the message index answers the MESSAGE root`() {
        val byUser = mock(ChatMessageByUserRepository::class.java) as ChatMessageByUserRepository<Long>
        val byTopic = mock(ChatMessageByTopicRepository::class.java) as ChatMessageByTopicRepository<Long>
        given(byTopic.findByKeyDest(2L)).willReturn(Flux.just(ChatMessageByTopic(ChatMessageByTopicKey(13L, 1L, 2L, Instant.EPOCH), "t", true)))

        val key = MessageIndex({ it.toLong() }, byUser, byTopic, roots).findBy(mapOf(MessageIndexService.TOPIC to "2")).blockFirst()!!
        assertThat(key.root).isEqualTo(root(ChatDomain.MESSAGE))
        assertThat(key.dest).isEqualTo(2L)
    }

    @Test
    fun `the membership index answers the TOPIC_MEMBERSHIP root`() {
        val byMember = mock(TopicMembershipByMemberRepository::class.java) as TopicMembershipByMemberRepository<Long>
        val byMemberOf = mock(TopicMembershipByMemberOfRepository::class.java) as TopicMembershipByMemberOfRepository<Long>
        given(byMember.findByMember(3L)).willReturn(Flux.just(TopicMembershipByMember(14L, 3L, 4L)))

        val key = MembershipIndex({ it.toLong() }, byMember, byMemberOf, roots).findBy(mapOf(MembershipIndexService.MEMBER to "3")).blockFirst()!!
        assertThat(key.root).isEqualTo(root(ChatDomain.TOPIC_MEMBERSHIP))
    }

    @Test
    fun `the grant index answers the AUTH_METADATA root`() {
        val byTarget = mock(AuthMetadataByTargetRepository::class.java) as AuthMetadataByTargetRepository<Long>
        val byPrincipal = mock(AuthMetadataByPrincipalRepository::class.java) as AuthMetadataByPrincipalRepository<Long>
        val user = root(ChatDomain.USER)
        given(byPrincipal.findByPrincipalId(5L)).willReturn(Flux.just(AuthMetadataByPrincipal(15L, 6L, 5L, user, user, "GET", false, Long.MAX_VALUE)))

        val key = AuthMetadataIndex(LongUtil(), byTarget, byPrincipal, roots).findBy(mapOf(AuthMetaIndex.PRINCIPAL to "5")).blockFirst()!!
        assertThat(key.root).isEqualTo(root(ChatDomain.AUTH_METADATA))
    }

    @Test
    fun `the key-value index answers the KEY_VALUE_PAIR root`() {
        val byField = mock(KeyValueIndexRepository::class.java) as KeyValueIndexRepository<Long>
        val byId = mock(KeyValueIndexByIdRepository::class.java) as KeyValueIndexByIdRepository<Long>
        given(byField.findByKeyFieldAndKeyValue("f", "v")).willReturn(Flux.just(ChatKeyValueIndex(ChatKeyValueIndexKey("f", "v", 16L))))

        val key = KeyValueIndex(TypedKeyValueIndexFields(emptyList()) as KeyValueIndexFields, byField, byId, roots).findBy(mapOf("f" to "v")).blockFirst()!!
        assertThat(key.root).isEqualTo(root(ChatDomain.KEY_VALUE_PAIR))
    }
}
