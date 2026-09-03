package com.demo.chat.test.service.composite

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.composite.impl.VectorStoreMessageVectorIndexer
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class MessagingServiceVectorTests {

    private val messageIndex = mock<MessageIndexService<Long, String, Any>>()
    private val messagePersistence = mock<MessagePersistence<Long, String>>()
    private val pubsub = mock<TopicPubSubService<Long, String>>()
    private val store = MockVectorStore()
    private val mapper = MessageDocumentMapper<Long>(LongUtil(), "long")
    private val realIndexer = VectorStoreMessageVectorIndexer<Long>(store, mapper)

    private fun givenKey() {
        BDDMockito.given(messagePersistence.key()).willReturn(Mono.just(Key.funKey(100L)))
        BDDMockito.given(messagePersistence.add(any<Message<Long, String>>())).willReturn(Mono.empty())
        BDDMockito.given(messageIndex.add(any<Message<Long, String>>())).willReturn(Mono.empty())
        BDDMockito.given(pubsub.sendMessage(any<Message<Long, String>>())).willReturn(Mono.empty())
    }

    private fun request() = MessageSendRequest("hello apple", 20L, 30L)

    @Test
    fun `send calls persistence then index then vector then pubsub`() {
        val indexer = mock<MessageVectorIndexer<Long>>()
        BDDMockito.given(indexer.add(any<Message<Long, String>>())).willReturn(Mono.empty())
        givenKey()

        val service = MessagingServiceImpl(
            messageIndex, messagePersistence, pubsub,
            { ByStringRequest("unused") }, indexer
        )

        StepVerifier.create(service.send(request())).expectNext(Key.funKey(100L)).verifyComplete()

        val inOrder: InOrder = Mockito.inOrder(messagePersistence, messageIndex, indexer, pubsub)
        inOrder.verify(messagePersistence).add(any<Message<Long, String>>())
        inOrder.verify(messageIndex).add(any<Message<Long, String>>())
        inOrder.verify(indexer).add(any<Message<Long, String>>())
        inOrder.verify(pubsub).sendMessage(any<Message<Long, String>>())
    }

    @Test
    fun `inactive chain stays three steps when there is no indexer`() {
        givenKey()

        val service = MessagingServiceImpl(
            messageIndex, messagePersistence, pubsub,
            { ByStringRequest("unused") }
        )

        StepVerifier.create(service.send(request())).expectNext(Key.funKey(100L)).verifyComplete()

        val inOrder: InOrder = Mockito.inOrder(messagePersistence, messageIndex, pubsub)
        inOrder.verify(messagePersistence).add(any<Message<Long, String>>())
        inOrder.verify(messageIndex).add(any<Message<Long, String>>())
        inOrder.verify(pubsub).sendMessage(any<Message<Long, String>>())
        Assertions.assertThat(store.ids).isEmpty()
    }

    @Test
    fun `vector failure stops pubsub and fails send`() {
        val failing = mock<MessageVectorIndexer<Long>>()
        BDDMockito.given(failing.add(any<Message<Long, String>>()))
            .willReturn(Mono.error(Exception("vector down")))
        givenKey()

        val service = MessagingServiceImpl(
            messageIndex, messagePersistence, pubsub,
            { ByStringRequest("unused") }, failing
        )

        StepVerifier.create(service.send(request())).expectError().verify()
        Mockito.verify(pubsub, Mockito.never()).sendMessage(any<Message<Long, String>>())
        Mockito.verify(messagePersistence, Mockito.times(1)).add(any<Message<Long, String>>())
    }

    @Test
    fun `record false skips the vector write`() {
        val alert = Message.create(MessageKey.create(100L, 20L, 30L), "joined", false)

        StepVerifier.create(realIndexer.add(alert)).verifyComplete()

        Assertions.assertThat(store.ids).isEmpty()
    }

    @Test
    fun `recorded message enters the store on bounded elastic`() {
        val message = Message.create(MessageKey.create(100L, 20L, 30L), "hello apple", true)

        StepVerifier.create(realIndexer.add(message)).verifyComplete()

        Assertions.assertThat(store.ids).containsExactly("message:long:100")
        Assertions.assertThat(store.lastWriteThread).startsWith("boundedElastic")
    }

    @Test
    fun `remove deletes by document id`() {
        val message = Message.create(MessageKey.create(100L, 20L, 30L), "hello apple", true)
        StepVerifier.create(realIndexer.add(message)).verifyComplete()

        StepVerifier.create(realIndexer.remove(Key.funKey(100L))).verifyComplete()

        Assertions.assertThat(store.ids).isEmpty()
    }
}