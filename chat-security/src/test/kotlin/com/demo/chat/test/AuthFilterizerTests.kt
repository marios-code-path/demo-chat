package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.secure.AuthFilterizer
import com.demo.chat.service.AuthMetadata
import com.demo.chat.service.StringRoleAuthorizationMetadata
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.function.Supplier
import kotlin.random.Random

class AuthFilterizerTests {

    val keyGen = Supplier { Key.funKey(Random.nextLong()) }

    @Test
    fun `filterizer create`() {
        Assertions
            .assertThat(AuthFilterizer<Long, String>())
            .isNotNull
    }

    @Test
    fun `only element is removed during pass`() {
        val filterizer = AuthFilterizer<Long, String>()

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
        val filterizer = AuthFilterizer<Long, String>()

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
        val filterizer = AuthFilterizer<Long, String>()

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
    fun `should filter out anon dups`() {
        val filterizer = AuthFilterizer<Long, String>()

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
    fun `Should filter out non seq ids`() {
        val filterizer = AuthFilterizer<Long, String>()

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
    fun `Should filter out eqpired ids`() {
        val filterizer = AuthFilterizer<Long, String>()

        var aPrinciple = keyGen.get()
        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(aPrinciple, anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ANY", System.currentTimeMillis()-111024),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ANY", System.currentTimeMillis()-111024),
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ALL", System.currentTimeMillis()-111024),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "SUM", System.currentTimeMillis()-111024)
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .verifyComplete()
    }
}