package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.secure.AuthFilterizer
import com.demo.chat.service.AuthMetadata
import com.demo.chat.service.StringRoleAuthorizationMetadata
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import kotlin.random.Random

class AuthFilterizerTests {
    companion object {
        val atomicLong = AtomicLong(Random.nextLong())
    }

    private val keyGen = Supplier { Key.funKey(atomicLong.incrementAndGet()) }
    private val filterizer = AuthFilterizer<Long, String> { a, b -> (a.key.id - b.key.id).toInt() }

    @Test
    fun `filterizer create`() {
        Assertions
            .assertThat(filterizer)
            .isNotNull
    }

    @Test
    fun `single only element is removed during pass`() {

        val anonId = keyGen.get()
        val idSeq = sequenceOf(anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), keyGen.get(), "ALL", 0L)
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .verifyComplete()
    }

    @Test
    fun `should not filter out single anon element`() {

        val anonId = keyGen.get()
        val idSeq = sequenceOf(anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, keyGen.get(), "ALL", 0L)
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `should filter all but one anon (many left over)`() {

        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aTarget, aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ALL", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "NON", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "ALL", 0L)
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .expectNextCount(2)
            .verifyComplete()
    }

    @Test
    fun `should filter out anon duplicates`() {

        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aTarget, aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "ANY", 0L)
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `Should filter out non hit ids`() {

        var aPrinciple = keyGen.get()
        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(aPrinciple, anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ALL", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "SUM", 0L)
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .expectNextCount(3)
            .verifyComplete()
    }

    @Test
    fun `Should filter out all expired, resolve zero`() {

        var aPrinciple = keyGen.get()
        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(aPrinciple, anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ANY", 1),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ANY", 1),
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ALL", 1),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "SUM", 1)
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .verifyComplete()
    }

    @Test
    fun `many expired resolves one metadata`() {

        var aPrinciple = keyGen.get()
        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(aPrinciple, anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, anonId, "REQUEST"), // REM by next
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aPrinciple, "REQUEST", 1), // REMOVES last
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "SUM"),// REM by next
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "SUM", 1),// REMOVES last
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "SUM") // ok
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .expectNextCount(1)
            .verifyComplete()
    }
}