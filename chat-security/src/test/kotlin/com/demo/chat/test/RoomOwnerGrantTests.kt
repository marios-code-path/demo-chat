package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.ContextRoomOwnerGrant
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.SecondOwnerException
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import reactor.test.StepVerifier
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The room owner writer.
 *
 * **The row is written for the caller of the security context.** The writer
 * reads the identity once, through `ContextIdentity`. See `CHAT-zhjltbky`.
 */
class RoomOwnerGrantTests {

    /**
     * **The writer names the owner, the room, and the wildcard.**
     *
     * `CoreAuthorizationService.write` replaces an empty key with the key that
     * the store mints, so the root of the empty key is inert. Read `write`,
     * `CoreAuthorizationService.kt:79`. Only the `empty` flag is read.
     *
     * The expiry is 0, which the summarizer reads as never expiring.
     */
    @Test
    fun `the writer grants the wildcard to the creating caller`() {
        val store = MapAuthStore()
        val grant = writer(store)

        grant.grantOwner(ROOM).contextWrite(context(authenticated())).block()

        assertThat(store.rows).hasSize(1)
        val row = store.rows.values.single()
        assertThat(row.principal).describedAs("the owner").isEqualTo(CALLER)
        assertThat(row.target).describedAs("the room").isEqualTo(ROOM)
        assertThat(row.permission).describedAs("the permission").isEqualTo(AuthSummarizer.WILDCARD)
        assertThat(row.expires).describedAs("the expiry").isEqualTo(0L)
        assertThat(row.key.empty).describedAs("the minted grant key").isFalse()
    }

    /**
     * **A caller with no identity owns no room, and that is not an error.**
     * `ContextIdentity` answers an empty `Mono` for a denied caller, so the
     * `flatMap` never runs and the chain completes. See the no-identity
     * decision of `CHAT-zhjltbky`.
     */
    @Test
    fun `no identity writes no grant`() {
        val store = MapAuthStore()
        val grant = writer(store)

        grant.grantOwner(ROOM).block()

        assertThat(store.rows).isEmpty()
    }

    /**
     * **An anonymous caller owns no room, and that is not an error.**
     *
     * `ContextIdentity` rule 4 answers the `Anon` root key for an
     * `AnonymousAuthenticationToken`, and the RSocket server seam establishes
     * that token. So a caller with no credential reaches an identity rather
     * than an empty one. See `RSocketServerConfiguration.rsocketSecurityAuthentication`.
     *
     * **The `Anon` key is in the actor set of every query.** So an ownership
     * row for `Anon` would make every caller the owner of the room, and a close
     * could not reach it, because an identity is an `ENTITY` and the close is a
     * `DOMAIN_ROOT`.
     */
    @Test
    fun `an anonymous caller owns no room`() {
        val store = MapAuthStore()
        val grant = writer(store)

        grant.grantOwner(ROOM).contextWrite(context(anonymous())).block()

        assertThat(store.rows).isEmpty()
    }

    /**
     * **The same owner writes no second row.** Until 2026-10-04 this call wrote
     * a second wildcard row, and this test was the evidence of that gap. The
     * owner decided that a repeat is a no-op. See `CHAT-esengqpv`.
     */
    @Test
    fun `a second grant by the same owner writes no second row`() {
        val store = MapAuthStore()
        val grant = writer(store)

        grant.grantOwner(ROOM).contextWrite(context(authenticated())).block()
        grant.grantOwner(ROOM).contextWrite(context(authenticated())).block()

        assertThat(store.rows.values.map { it.principal to it.target }).containsExactly(CALLER to ROOM)
    }

    /** **A second owner is refused, and the first owner row stays.** */
    @Test
    fun `a second owner is refused with the target named`() {
        val store = MapAuthStore()
        writer(store).grantOwner(ROOM).contextWrite(context(authenticated())).block()

        StepVerifier.create(service(store).authorize(row(OTHER, ROOM, AuthSummarizer.WILDCARD), true))
            .expectErrorSatisfies { error ->
                assertThat(error).isInstanceOf(SecondOwnerException::class.java)
                assertThat((error as SecondOwnerException).target).isEqualTo(ROOM)
                assertThat(error.message).contains("Target $ROOM already has an owner")
            }
            .verify()
        assertThat(store.rows.values.map { it.principal }).containsExactly(CALLER)
    }

    /**
     * **A close row is not an owner row.** It names a domain root, so the rank
     * keeps the owner above it. The guard lets it through.
     */
    @Test
    fun `a close row on an owned room is written`() {
        val store = MapAuthStore()
        writer(store).grantOwner(ROOM).contextWrite(context(authenticated())).block()

        service(store).authorize(row(USER_ROOT, ROOM, AuthSummarizer.WILDCARD, System.currentTimeMillis()), true).block()

        assertThat(store.rows.values.map { it.principal }).containsExactly(CALLER, USER_ROOT)
    }

    /** **An expired owner row is not a live owner.** A new owner may then be written. */
    @Test
    fun `an expired owner does not block a new owner`() {
        val store = MapAuthStore()
        service(store).authorize(row(CALLER, ROOM, AuthSummarizer.WILDCARD, System.currentTimeMillis() - 1000L), true).block()

        service(store).authorize(row(OTHER, ROOM, AuthSummarizer.WILDCARD), true).block()

        assertThat(store.rows.values.map { it.principal }).containsExactly(CALLER, OTHER)
    }

    /** **A named permission is not an owner row.** Another principal may still hold it. */
    @Test
    fun `a named grant beside an owner is written`() {
        val store = MapAuthStore()
        writer(store).grantOwner(ROOM).contextWrite(context(authenticated())).block()

        service(store).authorize(row(OTHER, ROOM, "SEND"), true).block()

        assertThat(store.rows.values.map { it.principal to it.permission })
            .containsExactly(CALLER to AuthSummarizer.WILDCARD, OTHER to "SEND")
    }

    /** A grant row with an empty key, which the service replaces with a minted key. */
    private fun row(principal: Key<Long>, target: Key<Long>, permission: String, expires: Long = 0L): AuthMetadata<Long> =
        AuthMetadata.create(Key.empty(0L, 9L), principal, target, permission, false, expires)

    private fun writer(store: MapAuthStore): ContextRoomOwnerGrant<Long> =
        ContextRoomOwnerGrant(
            ContextIdentity(rootKeys()),
            service(store),
            rootKeys(),
            LongUtil(),
        )

    private fun service(store: MapAuthStore): CoreAuthorizationService<Long, Key<Long>> =
        CoreAuthorizationService(
            store, MapAuthIndex(store), { it }, { it }, { ANON }, { USER_ROOT },
            AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys())),
            TestVerifiers.holding(rootKeys(), listOf(ANON, CALLER, OTHER, ROOM, USER_ROOT)),
        )

    private fun rootKeys(): RootKeys<Long> = RootKeysFixture.ofLong(
        mapOf(
            ChatDomain.USER to USER_ROOT,
            ChatDomain.MESSAGE to MESSAGE_ROOT,
            ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT,
        ),
        admin = ADMIN,
        anon = ANON,
    )

    private fun authenticated() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(
            ChatUserDetails(User.create(CALLER, "u", "handle", "http://u"), listOf()), "secret", listOf()
        )
    )

    /** The token that the RSocket server seam installs for a caller with no credential. */
    private fun anonymous() = SecurityContextImpl(
        AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
    )

    private fun context(that: SecurityContext) = ReactiveSecurityContextHolder.withSecurityContext(Mono.just(that))

    /** The authorization store, as a map. */
    private class MapAuthStore : PersistenceStore<Long, AuthMetadata<Long>> {
        val rows: MutableMap<Key<Long>, AuthMetadata<Long>> = linkedMapOf()

        override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { TestKeys.key(1000L + rows.size) }
        override fun add(ent: AuthMetadata<Long>): Mono<Void> {
            rows[ent.key] = ent
            return Mono.empty()
        }

        override fun rem(key: Key<Long>): Mono<Void> {
            rows.remove(key)
            return Mono.empty()
        }

        override fun get(key: Key<Long>): Mono<out AuthMetadata<Long>> = Mono.justOrEmpty(rows[key])
        override fun all(): Flux<out AuthMetadata<Long>> = Flux.fromIterable(rows.values)
    }

    /** The authorization index, which answers by target key. */
    private class MapAuthIndex(private val store: MapAuthStore) :
        IndexService<Long, AuthMetadata<Long>, Key<Long>> {

        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.fromIterable(store.rows.values.filter { it.target == query }.map { it.key })

        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = findBy(query).next()
    }

    private companion object {
        /** The `User` domain root. An identity is an object of the `User` domain. */
        val USER_ROOT: Key<Long> = Key.root(3L)

        /** The `Message` domain root. */
        val MESSAGE_ROOT: Key<Long> = Key.root(4L)

        /** The `MessageTopic` domain root. A room carries it. */
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        val ADMIN: Key<Long> = Key.of(2L, 3L)
        val ANON: Key<Long> = Key.of(1L, 3L)

        /** The caller of every context of this test. */
        val CALLER: Key<Long> = Key.of(6L, 3L)

        /** A room. Its root is the `MessageTopic` root. */
        val ROOM: Key<Long> = Key.of(7L, 5L)

        /** A second user, who would be a second owner. */
        val OTHER: Key<Long> = Key.of(8L, 3L)
    }
}
