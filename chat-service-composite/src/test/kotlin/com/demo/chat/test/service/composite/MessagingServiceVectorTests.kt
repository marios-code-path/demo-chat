package com.demo.chat.test.service.composite

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.composite.impl.VectorStoreMessageVectorIndexer
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.VectorWriteMode
import com.demo.chat.test.service.composite.command.CommandFixtures
import com.demo.chat.test.service.composite.command.MessagingStack
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

class MessagingServiceVectorTests {
    private val stacks = mutableListOf<MessagingStack>()
    private val store = MockVectorStore()
    private val mapper = MessageDocumentMapper<Long>(LongUtil(), "long")
    private val indexState = InMemoryVectorIndexState<Long>()
    private val indexJobStore = FakeVectorIndexJobStore()
    private val realIndexer =
        VectorStoreMessageVectorIndexer<Long>(store, mapper, indexState, indexJobStore, VectorWriteMode.UPSERT)

    private fun stack(submitter: Key<Long>? = null) = MessagingStack(submitterKey = submitter).also { stacks += it }

    @AfterEach
    fun close() = stacks.forEach { it.close() }

    // D7. A refused sender or destination admits no command and writes nothing.
    @Test
    fun `send from an unknown sender mints nothing and writes nothing`() {
        val unknown = Key.of(424260L, CommandFixtures.ROOTS.of(ChatDomain.USER).id)
        val s = stack(unknown)

        StepVerifier.create(s.service.send(MessageSendRequest("hello", 424260L, MessagingStack.ROOM)))
            .verifyError(com.demo.chat.domain.KeyVerificationException::class.java)
        Assertions.assertThat(s.runtime.bus.committedMappings()).isZero()
        Assertions.assertThat(s.persistence.added).isEmpty()
        Assertions.assertThat(s.index.added).isEmpty()
    }

    @Test
    fun `send to a destination outside MESSAGE_TOPIC mints nothing and writes nothing`() {
        val s = stack()

        StepVerifier.create(s.service.send(MessageSendRequest("hello", MessagingStack.SENDER, MessagingStack.SENDER)))
            .verifyError(com.demo.chat.domain.KeyVerificationException::class.java)
        Assertions.assertThat(s.runtime.bus.committedMappings()).isZero()
        Assertions.assertThat(s.persistence.added).isEmpty()
        Assertions.assertThat(s.index.added).isEmpty()
    }

    @Test
    fun `listen to an unknown topic opens no listener`() {
        val s = stack()

        StepVerifier.create(s.service.listenTopic(com.demo.chat.domain.ByIdRequest(424261L)))
            .verifyError(com.demo.chat.domain.KeyVerificationException::class.java)
        Assertions.assertThat(s.pubsub.subscribers.get()).isZero()
    }

    @Test
    fun `record false skips the vector write`() {
        val alert = Message.create(TestKeys.message(100L, 20L, 30L), "joined", false)

        StepVerifier.create(realIndexer.add(alert)).verifyComplete()

        Assertions.assertThat(store.ids).isEmpty()
    }

    @Test
    fun `recorded message enters the store on bounded elastic`() {
        val message = Message.create(TestKeys.message(100L, 20L, 30L), "hello apple", true)

        StepVerifier.create(realIndexer.add(message)).verifyComplete()

        Assertions.assertThat(store.ids).containsExactly("message:long:100")
        Assertions.assertThat(store.lastWriteThread).startsWith("boundedElastic")
    }

    @Test
    fun `remove deletes by document id`() {
        val message = Message.create(TestKeys.message(100L, 20L, 30L), "hello apple", true)
        StepVerifier.create(realIndexer.add(message)).verifyComplete()

        StepVerifier.create(realIndexer.remove(TestKeys.key(100L))).verifyComplete()

        Assertions.assertThat(store.ids).isEmpty()
    }
}
