package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.security.access.composite.MessageServiceAccess
import com.demo.chat.security.access.composite.TopicServiceAccess
import com.demo.chat.security.access.core.PubSubAccess
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.expression.BeanResolver
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicLong

/**
 * The two send checks, read from the annotations and evaluated as SpEL.
 *
 * **A method call to the broker fails when the target is a raw id.** The
 * compiled signature of the two argument form takes a `Key`, and a raw id
 * cannot bind to it. This class evaluates the real annotation text, so a
 * change to the text fails here. See `CHAT-zhjltbky`.
 */
class SendCheckExpressionTests {

    /**
     * **Both send checks name the room.** The room carries the
     * `MessageTopic` root, and `{User, MessageTopic, ALL}` names that root.
     */
    @Test
    fun `both send checks allow a room owner`() {
        val access = access(listOf(grant(CALLER, ROOM, "*")))

        assertThat(evaluate(sendExpression(), "req", request(), access))
            .describedAs("the message send expression")
            .isTrue()
        assertThat(evaluate(sendMessageExpression(), "message", message(), access))
            .describedAs("the pubsub send expression")
            .isTrue()
    }

    /** A caller with no row on the room is denied by both checks. */
    @Test
    fun `both send checks deny a caller with no row`() {
        val access = access(listOf(grant(USER_ROOT, TOPIC_ROOT, "ALL")))

        assertThat(evaluate(sendExpression(), "req", request(), access)).isFalse()
        assertThat(evaluate(sendMessageExpression(), "message", message(), access)).isFalse()
    }

    /**
     * **A row on the `Message` root does not cover a send.** The room is
     * another domain, so the shipped `{User, Message, SEND}` row does not
     * reach it.
     */
    @Test
    fun `a message root row does not cover a send to a room`() {
        val access = access(listOf(grant(USER_ROOT, MESSAGE_ROOT, "SEND")))

        assertThat(evaluate(sendExpression(), "req", request(), access)).isFalse()
        assertThat(evaluate(sendMessageExpression(), "message", message(), access)).isFalse()
    }

    /** The annotation text of `MessageServiceAccess.send`. */
    private fun sendExpression(): String =
        MessageServiceAccess::class.java.methods
            .first { it.name == "send" && it.parameterCount == 1 }
            .getAnnotation(PreAuthorize::class.java).value

    /** The annotation text of `PubSubAccess.sendMessage`. */
    private fun sendMessageExpression(): String =
        PubSubAccess::class.java.methods
            .first { it.name == "sendMessage" && it.parameterCount == 1 }
            .getAnnotation(PreAuthorize::class.java).value

    /**
     * **A raw id must bind.** The two argument `hasAccessTo` compiles to
     * `hasAccessTo(Key, String)`, and SpEL resolves a method by name, then by
     * argument count, then by assignability. A `Long` is not a `Key`, so the
     * call raises `EL1004E` and every caller is refused with no cause.
     */
    @Test
    fun `the room members expression binds a raw id`() {
        val access = access(listOf(grant(CALLER, ROOM, "MEMBERS")))

        assertThat(
            evaluate(
                expressionOf(TopicServiceAccess::class.java, "roomMembers"),
                "req", ByIdRequest(ROOM.id), access
            )
        ).describedAs("the room members expression").isTrue()
    }

    @Test
    fun `the get room expression binds a raw id`() {
        val access = access(listOf(grant(CALLER, ROOM, "GET")))

        assertThat(
            evaluate(
                expressionOf(TopicServiceAccess::class.java, "getRoom"),
                "req", ByIdRequest(ROOM.id), access
            )
        ).describedAs("the get room expression").isTrue()
    }

    @Test
    fun `the message by id expression binds a raw id`() {
        val access = access(listOf(grant(CALLER, ROOM, "GET")))

        assertThat(
            evaluate(
                expressionOf(MessageServiceAccess::class.java, "messageById"),
                "req", ByIdRequest(ROOM.id), access
            )
        ).describedAs("the message by id expression").isTrue()
    }

    @Test
    fun `the delete room expression binds a raw id`() {
        val access = access(listOf(grant(CALLER, ROOM, "REM")))

        assertThat(
            evaluate(
                expressionOf(TopicServiceAccess::class.java, "deleteRoom"),
                "req", ByIdRequest(ROOM.id), access
            )
        ).describedAs("the delete room expression").isTrue()
    }

    @Test
    fun `the listen topic expression binds a raw id`() {
        val access = access(listOf(grant(CALLER, ROOM, "SUBSCRIBE")))

        assertThat(
            evaluate(
                expressionOf(MessageServiceAccess::class.java, "listenTopic"),
                "req", ByIdRequest(ROOM.id), access
            )
        ).describedAs("the listen topic expression").isTrue()
    }

    /** The annotation text of a method of an access interface. */
    private fun expressionOf(type: Class<*>, name: String, arity: Int = 1): String =
        type.methods
            .first { it.name == name && it.parameterCount == arity }
            .getAnnotation(PreAuthorize::class.java).value

    /**
     * Evaluate one expression against the bean, as Spring does for `@chatAccess`.
     */
    private fun evaluate(
        expression: String,
        variable: String,
        value: Any,
        access: SpringSecurityAccessBrokerService<Long>
    ): Boolean {
        val context = StandardEvaluationContext()
        // The resolver answers a non-null bean. `BeanResolver` declares `Any`,
        // so a null arm does not type check. A test asks for one name.
        context.setBeanResolver(BeanResolver { _, name ->
            if (name == "chatAccess") access else error("no bean named $name")
        })
        context.setVariable(variable, value)

        @Suppress("UNCHECKED_CAST")
        val answer = SpelExpressionParser().parseExpression(expression).getValue(context) as Mono<Boolean>

        return answer
            .contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(
                    Mono.just(SecurityContextImpl(UsernamePasswordAuthenticationToken(details(), "secret", listOf())))
                )
            )
            .block() ?: false
    }

    // `MessageSendRequest` carries raw ids, and `dest` is the room id. The
    // shell builds one the same way, with `topic.key.id`.
    private fun request(): MessageSendRequest<Long, String> = MessageSendRequest("hello", CALLER.id, ROOM.id)

    // `MessageKey.of` takes raw ids for all four values, and `dest` is the
    // room id. Production builds one the same way, with `req.roomId`.
    private fun message(): Message<Long, String> = Message.create(
        com.demo.chat.domain.MessageKey.of(11L, 4L, CALLER.id, ROOM.id), "hello", true
    )

    private fun details() = ChatUserDetails(User.create(CALLER, "u", "handle", "http://u"), listOf())

    private fun access(rows: List<AuthMetadata<Long>>): SpringSecurityAccessBrokerService<Long> {
        val store = MapAuthStore()
        val index = MapAuthIndex(store)
        rows.forEach { store.rows[it.key] = it }
        val service = CoreAuthorizationService(
            store, index, { it }, { it }, { ANON }, { USER_ROOT },
            AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys())),
            TestVerifiers.holding(rootKeys(), listOf(ANON, CALLER, ROOM, MESSAGE)),
        )
        return SpringSecurityAccessBrokerService(
            AuthMetadataAccessBroker(service, TestVerifiers.resolvingNothing()),
            rootKeys(),
            TestVerifiers.holding(rootKeys(), listOf(ANON, CALLER, ROOM, MESSAGE)),
        )
    }

    private fun grant(principal: Key<Long>, target: Key<Long>, permission: String, expires: Long = 0L) =
        StringRoleAuthorizationMetadata(
            TestKeys.key(nextKey.getAndIncrement()), principal, target, permission, false, expires
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

    private class MapAuthStore : PersistenceStore<Long, AuthMetadata<Long>> {
        val rows: MutableMap<Key<Long>, AuthMetadata<Long>> = linkedMapOf()

        override fun key(): Mono<out Key<Long>> = Mono.just(TestKeys.key(nextKey.getAndIncrement()))
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

    private open class MapAuthIndex(private val store: MapAuthStore) :
        IndexService<Long, AuthMetadata<Long>, Key<Long>> {

        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.fromIterable(store.rows.values.filter { it.target == query }.map { it.key })

        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = findBy(query).next()
    }

    private companion object {
        val nextKey = AtomicLong(500L)

        val USER_ROOT: Key<Long> = Key.root(3L)
        val MESSAGE_ROOT: Key<Long> = Key.root(4L)
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        val ADMIN: Key<Long> = Key.of(2L, 3L)
        val ANON: Key<Long> = Key.of(1L, 3L)
        val CALLER: Key<Long> = Key.of(6L, 3L)
        val ROOM: Key<Long> = Key.of(7L, 5L)
        val MESSAGE: Key<Long> = Key.of(8L, 4L)
    }
}
