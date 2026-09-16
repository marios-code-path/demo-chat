package com.demo.chat.test.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyKeyValueIndexService
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import com.demo.chat.test.service.composite.FakeKeyValueStore
import com.demo.chat.test.service.composite.FakeMessageIndex
import com.demo.chat.test.service.composite.FakeMessagePersistence
import com.demo.chat.test.service.composite.FakePubSub
import com.demo.chat.test.service.composite.FakeTopicIndex
import com.demo.chat.test.service.composite.FakeTopicPersistence
import reactor.core.publisher.Mono

/**
 * The provider beans the vector configuration reads, backed by the doubles in
 * VectorTestFakes.
 *
 * The configuration calls six provider methods. Every other member answers with
 * an existing dummy, because nothing under test reaches it.
 */
internal class InMemoryServiceBeans {
    val topics = FakeTopicPersistence()
    val topicIndex = FakeTopicIndex()
    val pubsub = FakePubSub()
    val messages = FakeMessagePersistence()
    val messageIndex = FakeMessageIndex()
    val keyValues = FakeKeyValueStore()

    fun persistence(): PersistenceServiceBeans<Long, String> =
        object : PersistenceServiceBeans<Long, String> {
            override fun userPersistence(): UserPersistence<Long> =
                object : DummyPersistenceStore<Long, User<Long>>(), UserPersistence<Long> {}

            override fun topicPersistence(): TopicPersistence<Long> = topics

            override fun messagePersistence(): MessagePersistence<Long, String> = messages

            override fun membershipPersistence(): MembershipPersistence<Long> =
                object : DummyPersistenceStore<Long, TopicMembership<Long>>(), MembershipPersistence<Long> {}

            override fun authMetaPersistence(): AuthMetaPersistence<Long> =
                object : DummyPersistenceStore<Long, AuthMetadata<Long>>(), AuthMetaPersistence<Long> {}

            override fun keyValuePersistence(): KeyValueStore<Long, Any> = keyValues
        }

    fun index(): IndexServiceBeans<Long, String, Map<String, String>> =
        object : IndexServiceBeans<Long, String, Map<String, String>> {
            override fun userIndex(): UserIndexService<Long, Map<String, String>> =
                object : DummyIndexService<Long, User<Long>, Map<String, String>>(),
                    UserIndexService<Long, Map<String, String>> {}

            override fun messageIndex(): MessageIndexService<Long, String, Map<String, String>> = messageIndex

            override fun topicIndex(): TopicIndexService<Long, Map<String, String>> = topicIndex

            // MembershipIndexService declares an extra size member, which is
            // why this one dummy overrides a method.
            override fun membershipIndex(): MembershipIndexService<Long, Map<String, String>> =
                object : DummyIndexService<Long, TopicMembership<Long>, Map<String, String>>(),
                    MembershipIndexService<Long, Map<String, String>> {
                    override fun size(query: Map<String, String>): Mono<Long> = Mono.just(0L)
                }

            override fun authMetadataIndex(): AuthMetaIndex<Long, Map<String, String>> =
                object : DummyIndexService<Long, AuthMetadata<Long>, Map<String, String>>(),
                    AuthMetaIndex<Long, Map<String, String>> {}

            override fun KVPairIndex(): KeyValueIndexService<Long, Map<String, String>> =
                DummyKeyValueIndexService()
        }

    fun pubSub(): PubSubServiceBeans<Long, String> =
        object : PubSubServiceBeans<Long, String> {
            override fun pubSubService(): TopicPubSubService<Long, String> = pubsub
        }
}
