package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.Key
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.rank.PrincipalRank
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import kotlin.random.Random

class AuthSummarizerTests {
    companion object {
        val atomicLong = AtomicLong(Random.nextLong(1024,999999))
    }

    private val keyGen = Supplier { Key.funKey(atomicLong.incrementAndGet()) }

    private val USER_ROOT = keyGen.get()
    private val ADMIN_KEY = keyGen.get()
    private val ANON_KEY = keyGen.get()

    private val rootKeys = RootKeysFixture.of(
        mapOf(ChatDomain.USER to USER_ROOT, ChatDomain.MESSAGE_TOPIC to keyGen.get()),
        admin = ADMIN_KEY,
        anon = ANON_KEY
    ) { keyGen.get() }

    /** The whole rank. Level 3 is the injected key.id order until CHAT-ojbgbznh. */
    private val ranked = AuthSummarizer<Long>(
        { a, b -> a.key.id.compareTo(b.key.id) },
        PrincipalRank(rootKeys)
    )
    private val filterizer = AuthSummarizer<Long>(
        { a, b ->
            when {
                a.key.id < b.key.id -> -1
                a.key.id > b.key.id -> 1
                else -> 0
            }
        },
        PrincipalRank(rootKeys)
    )

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

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), keyGen.get(), "ALL", 0L)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq))
            .verifyComplete()
    }

    @Test
    fun `should not filter out single anon element`() {

        val anonId = keyGen.get()
        val idSeq = sequenceOf(anonId)

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, keyGen.get(), "ALL", 0L)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq))
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `should filter all but one anon (many left over)`() {

        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(anonId)

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aTarget, aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ALL", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "NON", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "ALL", 0L)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq))
            .expectNextCount(2)
            .verifyComplete()
    }

    @Test
    fun `should filter out anon duplicates`() {

        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(anonId)

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aTarget, aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "ANY", 0L)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq))
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `Should filter out non hit ids`() {

        var aPrinciple = keyGen.get()
        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(aPrinciple, anonId, aTarget)

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ANY", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ALL", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "SUM", 0L)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq))
            .expectNextCount(3)
            .verifyComplete()
    }

    @Test
    fun `Should filter out all expired, resolve zero`() {

        var aPrinciple = keyGen.get()
        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(aPrinciple, anonId, aTarget)

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ANY", 1),
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), aTarget, "ANY", 1),
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "ALL", 1),
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "SUM", 1)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq))
            .verifyComplete()
    }

    @Test
    fun `many expired resolves one metadata`() {

        var aPrinciple = keyGen.get()
        var aTarget = keyGen.get()
        val anonId = keyGen.get()
        val idSeq = sequenceOf(aPrinciple, anonId, aTarget)

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, anonId, "REQUEST"), // REM by next
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aPrinciple, "REQUEST", 1), // REMOVES last
            StringRoleAuthorizationMetadata(keyGen.get(), aPrinciple, aTarget, "SUM"),// REM by next
            StringRoleAuthorizationMetadata(keyGen.get(), aTarget, aTarget, "SUM", 1),// REMOVES last
            StringRoleAuthorizationMetadata(keyGen.get(), anonId, aTarget, "SUM") // ok
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq))
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `a live wildcard row answers a permission that no row names`() {

        val aPrincipal = keyGen.get()
        val aTarget = keyGen.get()
        val idSeq = sequenceOf(aPrincipal)

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aPrincipal, aTarget, "*", 0L)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq, "SEND"))
            .assertNext { meta -> Assertions.assertThat(meta.permission).isEqualTo("SEND") }
            .verifyComplete()
    }

    @Test
    fun `an expired wildcard row removes a permission that an earlier row granted`() {

        val aPrincipal = keyGen.get()
        val aTarget = keyGen.get()
        val idSeq = sequenceOf(aPrincipal)

        val grantKey = keyGen.get()
        val closeKey = keyGen.get()

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(grantKey, aPrincipal, aTarget, "JOIN", 0L),
            StringRoleAuthorizationMetadata(closeKey, aPrincipal, aTarget, "*", 1L)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq, "JOIN"))
            .verifyComplete()
    }

    @Test
    fun `a wildcard row keeps its permission when no permission is requested`() {

        val aPrincipal = keyGen.get()
        val aTarget = keyGen.get()
        val idSeq = sequenceOf(aPrincipal)

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), aPrincipal, aTarget, "*", 0L)
        )

        StepVerifier.create(filterizer.computeAggregates(filterData, idSeq))
            .assertNext { meta -> Assertions.assertThat(meta.permission).isEqualTo("*") }
            .verifyComplete()
    }

    /**
     * The rank rule of 2026-09-24. A wildcard row beats a named permission.
     * ENTITY beats DOMAIN_ROOT. Later beats earlier.
     *
     * Every case below puts the `User` root in the actor set by hand. The
     * production actor set does not hold it yet. CHAT-mahevldm carries that.
     */
    @Test
    fun `the owner keeps the target after a close`() {

        val owner = keyGen.get()
        val room = keyGen.get()

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), owner, room, "*", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), USER_ROOT, room, "*", 1L)
        )

        StepVerifier.create(ranked.computeAggregates(filterData, sequenceOf(owner, USER_ROOT), "SEND"))
            .assertNext { meta -> Assertions.assertThat(meta.permission).isEqualTo("SEND") }
            .verifyComplete()
    }

    @Test
    fun `a caller with a named grant holds nothing after a close`() {

        val other = keyGen.get()
        val room = keyGen.get()

        // The close is written first, and the named grant after it. A wildcard
        // survives every named row, so the later grant has no effect.
        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), USER_ROOT, room, "*", 1L),
            StringRoleAuthorizationMetadata(keyGen.get(), other, room, "JOIN", 0L)
        )

        StepVerifier.create(ranked.computeAggregates(filterData, sequenceOf(other, USER_ROOT), "JOIN"))
            .verifyComplete()
    }

    @Test
    fun `a named row with an expiry has no effect beside a live wildcard`() {

        val caller = keyGen.get()
        val target = keyGen.get()

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), caller, target, "*", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), caller, target, "REMOVE", 1L)
        )

        StepVerifier.create(ranked.computeAggregates(filterData, sequenceOf(caller), "REMOVE"))
            .assertNext { meta -> Assertions.assertThat(meta.permission).isEqualTo("REMOVE") }
            .verifyComplete()
    }

    @Test
    fun `a later expired wildcard ends an earlier live wildcard`() {

        val caller = keyGen.get()
        val target = keyGen.get()

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), caller, target, "*", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), caller, target, "*", 1L)
        )

        StepVerifier.create(ranked.computeAggregates(filterData, sequenceOf(caller), "REMOVE"))
            .verifyComplete()
    }

    /**
     * The administrator invariant. `Admin` names one user, so it is an ENTITY
     * principal. A close names a DOMAIN_ROOT principal, so the administrator
     * outranks it and a closed target stays administrable.
     *
     * This test fails if the rank reads `RootKeys` membership alone, because
     * `Admin` is a root key and it is not a domain.
     */
    @Test
    fun `the administrator outranks a close`() {

        val target = keyGen.get()

        val filterData: Flux<AuthMetadata<Long>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), ADMIN_KEY, target, "*", 0L),
            StringRoleAuthorizationMetadata(keyGen.get(), USER_ROOT, target, "*", 1L)
        )

        StepVerifier.create(ranked.computeAggregates(filterData, sequenceOf(ADMIN_KEY, USER_ROOT), "SEND"))
            .assertNext { meta -> Assertions.assertThat(meta.permission).isEqualTo("SEND") }
            .verifyComplete()
    }
}
