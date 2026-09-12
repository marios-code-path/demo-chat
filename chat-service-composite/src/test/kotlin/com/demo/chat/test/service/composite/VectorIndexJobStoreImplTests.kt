package com.demo.chat.test.service.composite

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.service.composite.impl.VectorIndexJobStoreImpl
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.VectorIndexJobStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class VectorIndexJobStoreImplTests {
    private val startedAt = Instant.parse("2026-09-11T12:00:00Z")
    private val finishedAt = Instant.parse("2026-09-11T12:00:01Z")

    @Test
    fun `createJob writes a topic, opens it, and stores a running job`() {
        val store = storeUnderTest()

        val job = store.createJob(startedAt).block()!!

        Assertions.assertThat(job.outcome).isEqualTo(JobOutcome.RUNNING)
        Assertions.assertThat(job.nodeId).isEqualTo(7)
        Assertions.assertThat(job.keyType).isEqualTo("long")
        Assertions.assertThat(topics.saved).hasSize(1)
        Assertions.assertThat(JobTopicNames.isJobTopic(topics.saved.first().data)).isTrue()
        Assertions.assertThat(topicIndex.saved).hasSize(1)
        Assertions.assertThat(pubsub.opened).containsExactly(job.key.id)
        Assertions.assertThat(store.readJob(job.key).block()!!.key).isEqualTo(job.key)
    }

    @Test
    fun `write replaces the stored job under the same key`() {
        val store = storeUnderTest()
        val job = store.createJob(startedAt).block()!!

        store.write(job.copy(outcome = JobOutcome.SUCCEEDED, indexed = 5L)).block()

        val read = store.readJob(job.key).block()!!
        Assertions.assertThat(read.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(read.indexed).isEqualTo(5L)
    }

    @Test
    fun `invalidate raises the count on the named job only`() {
        val store = storeUnderTest()
        val first = store.createJob(startedAt).block()!!
        val second = store.createJob(startedAt.plusSeconds(1)).block()!!

        store.invalidate(first.key, finishedAt).block()

        Assertions.assertThat(store.readJob(first.key).block()!!.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(store.readJob(first.key).block()!!.lastInvalidationAt).isEqualTo(finishedAt)
        Assertions.assertThat(store.readJob(second.key).block()!!.invalidationCount).isEqualTo(0L)
    }

    // Interleaving one. The invalidation lands first. The terminal write must
    // not lower the count back to the value the run carried.
    @Test
    fun `a terminal write keeps an invalidation that arrived first`() {
        val store = storeUnderTest()
        val job = store.createJob(startedAt).block()!!

        store.invalidate(job.key, finishedAt).block()
        store.finishJob(job.copy(outcome = JobOutcome.SUCCEEDED, indexed = 3L)).block()

        val read = store.readJob(job.key).block()!!
        Assertions.assertThat(read.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(read.indexed).isEqualTo(3L)
        Assertions.assertThat(read.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(read.covers).isFalse()
    }

    // Interleaving two. The terminal write lands first, and the invalidation
    // then applies to the job that just became the target.
    @Test
    fun `an invalidation after a terminal write raises the count`() {
        val store = storeUnderTest()
        val job = store.createJob(startedAt).block()!!

        store.finishJob(job.copy(outcome = JobOutcome.SUCCEEDED, indexed = 3L)).block()
        store.invalidate(job.key, finishedAt).block()

        val read = store.readJob(job.key).block()!!
        Assertions.assertThat(read.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(read.covers).isFalse()
    }

    // The two tests above call the operations in order, so they prove the merge
    // rule and nothing about contention. This one overlaps them. The store
    // reads through a publisher that completes only when the test releases it,
    // so a writer that did not wait would read before the first write landed.
    @Test
    fun `an overlapping invalidation does not interleave with a terminal write`() {
        // Each read waits on its own timer rather than on one shared gate. A
        // single sink resumes its subscribers one after another on the
        // emitting thread, so two operations behind it never truly overlap and
        // the test passes even without serialization.
        val store = storeUnderTest(readDelay = Mono.delay(Duration.ofMillis(50)).then())
        val job = store.createJob(startedAt).block()!!

        val terminal = store.finishJob(job.copy(outcome = JobOutcome.SUCCEEDED, indexed = 3L)).toFuture()
        val invalidation = store.invalidate(job.key, finishedAt).toFuture()

        terminal.get(10, TimeUnit.SECONDS)
        invalidation.get(10, TimeUnit.SECONDS)

        val read = store.readJob(job.key).block()!!
        Assertions.assertThat(read.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(read.indexed).isEqualTo(3L)
        // The invalidation ran after the terminal write, on the value that
        // write produced. A lost update would leave this at zero.
        Assertions.assertThat(read.invalidationCount).isEqualTo(1L)
    }

    // Every reserved topic, from every node. The scan exclusion needs all of
    // them, because another node's job records sit in the same message store.
    @Test
    fun `listJobTopics returns every reserved topic and no user topic`() {
        val store = storeUnderTest()
        store.createJob(startedAt).block()
        topics.saved.add(MessageTopic.create(Key.funKey(99L), "general"))
        topics.saved.add(MessageTopic.create(Key.funKey(98L), JobTopicNames.nameFor(8, "long", startedAt, "other")))

        StepVerifier.create(store.listJobTopics()).expectNextCount(2).verifyComplete()
    }

    // The storage key and the stored root key are two separate facts, and only
    // this read can compare them. A record under key A that holds key B makes
    // the coverage policy invalidate B. Key A stays clean, and it covers the
    // index again after a restart.
    @Test
    fun `a job whose root key differs from its storage key fails the read`() {
        val store = storeUnderTest()
        val job = store.createJob(startedAt).block()!!
        val other = Key.funKey(4242L)

        keyValues.values[job.key.id] = KeyValuePair.create(job.key, job.copy(key = other) as Any)

        StepVerifier
            .create(store.readJob(job.key))
            .expectErrorSatisfies { error ->
                Assertions.assertThat(error).isInstanceOf(ChatException::class.java)
                Assertions.assertThat(error.message).contains(job.key.id.toString(), "4242")
            }
            .verify()
    }

    // The cassandra backend stores the JSON text. The decoded key must still
    // equal the storage key, or the check above would fail every read on that
    // backend.
    @Test
    fun `a job stored as json text passes the root key check`() {
        val store = storeUnderTest()
        val job = store.createJob(startedAt).block()!!

        keyValues.values[job.key.id] =
            KeyValuePair.create(job.key, mapper.writeValueAsString(job) as Any)

        Assertions.assertThat(store.readJob(job.key).block()!!.key).isEqualTo(job.key)
    }

    private val topics = FakeTopicPersistence()
    private val topicIndex = FakeTopicIndex()
    private val pubsub = FakePubSub()

    /** The store of the most recent [storeUnderTest]. Each test builds one. */
    private lateinit var keyValues: FakeKeyValueStore

    // The same modules the deployments register. A bare mapper cannot read a
    // Key, and the cassandra shape is a JSON string.
    private val mapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }

    private fun storeUnderTest(readDelay: Mono<Void> = Mono.empty()): VectorIndexJobStore<Long> {
        keyValues = FakeKeyValueStore(readDelay)
        return VectorIndexJobStoreImpl(
            topicPersistence = topics,
            topicIndex = topicIndex,
            pubsub = pubsub,
            keyValueStore = keyValues,
            codec = IndexJobCodec(mapper),
            nodeId = 7,
            keyType = "long",
            incarnationId = "incarnation-a",
            workerKey = Key.funKey(1000L),
        )
    }
}
