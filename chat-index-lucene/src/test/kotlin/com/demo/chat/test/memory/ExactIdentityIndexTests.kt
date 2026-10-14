package com.demo.chat.test.memory

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexFieldKinds
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.User
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.MembershipLuceneIndex
import com.demo.chat.index.lucene.impl.MessageLuceneIndex
import com.demo.chat.index.lucene.impl.TopicLuceneIndex
import com.demo.chat.index.lucene.impl.UserLuceneIndex
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ExactIdentityIndexTests {
    private val queries = IndexSearchRequestConverters()

    @Test
    fun `topic names match the whole NFC value`() {
        val index = TopicLuceneIndex<Long>(
            IndexEntryEncoder.ofTopic(),
            { value -> TestKeys.key(value.toLong()) },
            { topic -> topic.key },
        ).openedInMemory()

        index.add(MessageTopic.create(TestKeys.key(1L), "e10-user:room")).block()
        index.add(MessageTopic.create(TestKeys.key(2L), "caf\u00e9:room")).block()
        index.add(MessageTopic.create(TestKeys.key(3L), "Case:room")).block()

        assertThat(index.findBy(queries.topicNameToQuery(ByStringRequest("e2-user:room"))).collectList().block()).isEmpty()
        assertThat(index.findBy(queries.topicNameToQuery(ByStringRequest("e10-user:room"))).collectList().block()!!.map { it.id })
            .containsExactly(1L)
        assertThat(index.findBy(queries.topicNameToQuery(ByStringRequest("cafe\u0301:room"))).collectList().block()!!.map { it.id })
            .containsExactly(2L)
        assertThat(index.findBy(queries.topicNameToQuery(ByStringRequest("case:room"))).collectList().block()).isEmpty()
    }

    @Test
    fun `handles match whole values without case distinction`() {
        val index = UserLuceneIndex<Long>(
            IndexEntryEncoder.ofUser(),
            { value -> TestKeys.key(value.toLong()) },
            { user -> user.key },
        ).openedInMemory()

        index.add(User.create(TestKeys.key(1L), "First", "e10-user", "localhost")).block()
        index.add(User.create(TestKeys.key(2L), "Second", "Admin", "localhost")).block()

        assertThat(index.findBy(queries.userHandleToQuery(ByStringRequest("e2-user"))).collectList().block()).isEmpty()
        assertThat(index.findBy(queries.userHandleToQuery(ByStringRequest("e10-user"))).collectList().block()!!.map { it.id })
            .containsExactly(1L)
        assertThat(index.findBy(queries.userHandleToQuery(ByStringRequest("admin"))).collectList().block()!!.map { it.id })
            .containsExactly(2L)
    }

    @Test
    fun `message ids use whole UUID values while data stays searchable`() {
        val index = MessageLuceneIndex<UUID>(
            IndexEntryEncoder.ofMessage(),
            { value -> TestKeys.key(UUIDUtil().fromString(value)) },
            { message -> message.key },
        ).openedInMemory()
        val first = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val second = UUID.fromString("550e8400-aaaa-bbbb-cccc-000000000001")
        val sender = UUID.fromString("10000000-0000-4000-8000-000000000001")
        val room = UUID.fromString("20000000-0000-4000-8000-000000000002")

        index.add(Message.create(TestKeys.message(first, sender, room), "orchard apple", true)).block()
        index.add(Message.create(TestKeys.message(second, sender, room), "orchard pear", true)).block()

        assertThat(index.findBy(IndexFieldKinds.MESSAGE.query(MessageIndexService.ID, first.toString())).collectList().block()!!.map { it.id })
            .containsExactly(first)
        assertThat(index.findBy(IndexFieldKinds.MESSAGE.query(MessageIndexService.DATA, "apple")).collectList().block()!!.map { it.id })
            .containsExactly(first)
    }

    @Test
    fun `membership pair matches both parts`() {
        val index = MembershipLuceneIndex<Long>(
            IndexEntryEncoder.ofTopicMembership(),
            { value -> TestKeys.key(value.toLong()) },
            { membership -> TestKeys.key(membership.key) },
        ).openedInMemory()
        index.add(TopicMembership.create(1L, 12L, 30L)).block()
        index.add(TopicMembership.create(2L, 12L, 31L)).block()

        val request = com.demo.chat.domain.MembershipRequest(12L, 30L)
        assertThat(index.findBy(queries.membershipRequestToQuery(request)).collectList().block()!!.map { it.id })
            .containsExactly(1L)
    }

    @Test
    fun `a plain identity field reports its exact name`() {
        val index = TopicLuceneIndex<Long>(
            IndexEntryEncoder.ofTopic(),
            { value -> TestKeys.key(value.toLong()) },
            { topic -> topic.key },
        ).openedInMemory()
        index.add(MessageTopic.create(TestKeys.key(1L), "room")).block()

        assertThatThrownBy { index.findBy(IndexSearchRequest("name", "room", 10)).collectList().block() }
            .hasCauseInstanceOf(ChatException::class.java)
            .hasMessageContaining("_x_name")
    }
}
