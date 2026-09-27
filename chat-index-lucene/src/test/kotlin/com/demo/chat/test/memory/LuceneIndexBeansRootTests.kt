package com.demo.chat.test.memory

import com.demo.chat.config.LuceneIndexBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.DefaultListableBeanFactory

/**
 * Each index that `LuceneIndexBeans` builds answers keys under the root of its
 * own domain. These are the production encoders. See `CHAT-avduuqwp`.
 */
class LuceneIndexBeansRootTests {

    private val roots = FakeKeyServices.longRoots()
    private val beans = LuceneIndexBeans(
        LongUtil(), roots, DefaultListableBeanFactory().getBeanProvider(KeyValueIndexFieldsEntry::class.java)
    )

    private fun root(domain: ChatDomain) = roots.of(domain).id

    @Test
    fun `the user index answers the USER root`() {
        val index = beans.userIndex()
        index.add(User.create(Key.of(11L, root(ChatDomain.USER)), "name", "handle11", "http://u")).block()
        assertThat(index.findBy(IndexSearchRequest(UserIndexService.HANDLE, "handle11", 10)).blockFirst()!!.root)
            .isEqualTo(root(ChatDomain.USER))
    }

    @Test
    fun `the topic index answers the MESSAGE_TOPIC root`() {
        val index = beans.topicIndex()
        index.add(MessageTopic.create(Key.of(12L, root(ChatDomain.MESSAGE_TOPIC)), "room12")).block()
        assertThat(index.findBy(IndexSearchRequest(TopicIndexService.NAME, "room12", 10)).blockFirst()!!.root)
            .isEqualTo(root(ChatDomain.MESSAGE_TOPIC))
    }

    @Test
    fun `the message index answers the MESSAGE root`() {
        val index = beans.messageIndex()
        index.add(Message.create(MessageKey.of(13L, root(ChatDomain.MESSAGE), 1L, 2L), "hello", true)).block()
        assertThat(index.findBy(IndexSearchRequest(MessageIndexService.TOPIC, "2", 10)).blockFirst()!!.root)
            .isEqualTo(root(ChatDomain.MESSAGE))
    }

    @Test
    fun `the membership index answers the TOPIC_MEMBERSHIP root`() {
        val index = beans.membershipIndex()
        index.add(TopicMembership.create(14L, 3L, 4L)).block()
        assertThat(index.findBy(IndexSearchRequest(MembershipIndexService.MEMBER, "3", 10)).blockFirst()!!.root)
            .isEqualTo(root(ChatDomain.TOPIC_MEMBERSHIP))
    }

    @Test
    fun `the grant index answers the AUTH_METADATA root`() {
        val index = beans.authMetadataIndex()
        val user = root(ChatDomain.USER)
        index.add(AuthMetadata.create(Key.of(15L, root(ChatDomain.AUTH_METADATA)), Key.of(5L, user), Key.of(6L, user), "GET", false, Long.MAX_VALUE)).block()
        assertThat(index.findBy(IndexSearchRequest(AuthMetaIndex.PRINCIPAL, "5", 10)).blockFirst()!!.root)
            .isEqualTo(root(ChatDomain.AUTH_METADATA))
    }
}
