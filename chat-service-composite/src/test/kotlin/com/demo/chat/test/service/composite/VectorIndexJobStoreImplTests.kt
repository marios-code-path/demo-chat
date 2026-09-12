package com.demo.chat.test.service.composite

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
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

    @Test
    fun `listJobTopics returns reserved topics of this node only`() {
        val store = storeUnderTest()
        store.createJob(startedAt).block()
        topics.saved.add(MessageTopic.create(Key.funKey(99L), "general"))
        topics.saved.add(MessageTopic.create(Key.funKey(98L), JobTopicNames.nameFor(8, "long", startedAt, "other")))

        StepVerifier.create(store.listJobTopics()).expectNextCount(1).verifyComplete()
    }

    private val topics = FakeTopicPersistence()
    private val topicIndex = FakeTopicIndex()
    private val pubsub = FakePubSub()

    private fun storeUnderTest(readDelay: Mono<Void> = Mono.empty()): VectorIndexJobStore<Long> =
        VectorIndexJobStoreImpl(
            topicPersistence = topics,
            topicIndex = topicIndex,
            pubsub = pubsub,
            keyValueStore = FakeKeyValueStore(readDelay),
            codec = IndexJobCodec(ObjectMapper().findAndRegisterModules()),
            nodeId = 7,
            keyType = "long",
            incarnationId = "incarnation-a",
            workerKey = Key.funKey(1000L),
        )
}
