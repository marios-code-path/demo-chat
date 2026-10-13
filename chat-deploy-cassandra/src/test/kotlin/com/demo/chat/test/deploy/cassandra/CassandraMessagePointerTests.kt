package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessagePointerRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.PointerMessage
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.security.ChatUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import reactor.util.context.Context
import java.time.Duration

/**
 * The pointer target check on a Cassandra store.
 *
 * **A Cassandra removal keeps the row and clears the `visible` flag.** The
 * message reader rebuilt every pointer with the flag set, so a removed pointer
 * read back as a live pointer. The creation check then answered "a pointer
 * cannot target a pointer" in place of "no such message". See `CHAT-erkoxybm`.
 *
 * This backend is the only one that removes this way. The memory and Redis
 * backends delete the row, so a removed pointer is already absent there.
 *
 * The calls go to the composite services, as a controller calls them. The
 * `FORWARD` check of the route sits outside this path, and the member holds it
 * through `joinRoom` in any case.
 *
 * Node id 29 belongs to this class. See docs/NODEID-CLAIM.md.
 */
@Tag("integration")
class CassandraMessagePointerTests : CassandraContainerBase() {

    private val timeout = Duration.ofSeconds(10)

    /** The launch surface of `CassandraMessageSendTests`, with node id 29. */
    private fun start(): ConfigurableApplicationContext =
        SpringApplicationBuilder(ChatApp::class.java)
            .web(WebApplicationType.NONE)
            .run(*arrayOf(
                "spring.config.location=classpath:/application.yml",
                "spring.config.additional-location=classpath:/config/logging.yml," +
                    "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
                "server.port=0",
                "spring.rsocket.server.port=0",
                "app.key.type=long",
                "app.nodeid=29",
                "app.users.create=true",
                "app.service.core.key=cassandra",
                "app.service.core.persistence=cassandra",
                "app.service.core.index=cassandra",
                "app.service.core.pubsub=memory",
                "app.service.core.secrets=cassandra",
                "app.service.composite", "app.command.bus=memory",
                "app.service.composite.auth=true",
                "app.controller.secrets",
                "app.controller.key",
                "app.controller.persistence",
                "app.controller.index",
                "app.controller.user",
                "app.controller.message",
                "app.controller.topic",
                "app.controller.pubsub",
                "app.service.security.userdetails",
                "spring.profiles.active=cassandra-contact-point",
                "spring.cassandra.contact-points=${cassandraContainer.host}",
                "spring.cassandra.port=${cassandraContainer.getMappedPort(9042)}",
                "spring.cassandra.username=${cassandraContainer.username}",
                "spring.cassandra.password=${cassandraContainer.password}",
            ).map { "--$it" }.toTypedArray())

    @Test
    fun `a pointer whose target was removed is refused as a missing target`() {
        start().use { context ->
            @Suppress("UNCHECKED_CAST")
            val composite = context.getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
            @Suppress("UNCHECKED_CAST")
            val stores = context.getBean(PersistenceServiceBeans::class.java) as PersistenceServiceBeans<Long, String>

            val member = composite.userService()
                .addUser(UserCreateRequest("pointer", "pointermember", "http://u"))
                .block(timeout)!!
            val host = composite.topicService().addRoom(ByStringRequest("ptrhost")).block(timeout)!!
            val source = composite.topicService().addRoom(ByStringRequest("ptrsource")).block(timeout)!!
            composite.topicService().joinRoom(MembershipRequest(member.id, host.id)).block(timeout)
            composite.topicService().joinRoom(MembershipRequest(member.id, source.id)).block(timeout)
            val asMember = ReactiveSecurityContextHolder.withAuthentication(
                UsernamePasswordAuthenticationToken(
                    ChatUserDetails(User.create(member, "pointermember", "pointermember", "http://u"), listOf()),
                    "n/a",
                    listOf(),
                )
            )

            val target = composite.messageService()
                .send(MessageSendRequest("pointer target", member.id, source.id))
                .contextWrite(asMember)
                .block(timeout)!!

            // The first pointer lives in the host room, and the member reads the target.
            val first = composite.messageService()
                .pointer(MessagePointerRequest(host.id, target.id, "Message", "pointer-one"))
                .contextWrite(asMember)
                .block(timeout)!!
            val firstKey = Key.of(first.receipt.messageKey.id, first.receipt.messageKey.root)

            // The control: a live pointer is refused as a target.
            assertThat(refusalOf(pointerCall(composite, host, firstKey.id, "pointer-two", asMember)))
                .describedAs("the refusal of a pointer to a live pointer")
                .contains("a pointer cannot target a pointer")

            // Cassandra keeps the row of a removed message and clears the flag.
            stores.messagePersistence().rem(firstKey).block(timeout)

            // The same call now reads a missing target, and not a pointer. A
            // reader that rebuilt the row with a live flag would refuse this
            // call as a pointer, which is the defect. See `CHAT-erkoxybm`.
            assertThat(refusalOf(pointerCall(composite, host, firstKey.id, "pointer-three", asMember)))
                .describedAs("the refusal of a pointer to a removed pointer")
                .contains("no such message")

            // The flag that carries the refusal, read after the call.
            val stored = stores.messagePersistence().get(firstKey).block(timeout)
            assertThat(stored).describedAs("the removed pointer row").isInstanceOf(PointerMessage::class.java)
            assertThat(stored!!.record).describedAs("the flag of the removed pointer").isFalse()

            // Both refusals left the host room as it was.
            assertThat(pointersIn(stores, host))
                .describedAs("the pointers of the host room")
                .hasSize(1)
        }
    }

    private fun pointerCall(
        composite: CompositeServiceBeans<Long, String>,
        host: Key<Long>,
        targetId: Long,
        requestId: String,
        context: Context,
    ): Mono<*> = composite.messageService()
        .pointer(MessagePointerRequest(host.id, targetId, "Message", requestId))
        .contextWrite(context)

    /**
     * A refusal is a checked `ChatException`, so a blocking call wraps it. The
     * unwrapped message is the one the caller reads.
     */
    private fun refusalOf(call: Mono<*>): String? =
        catchThrowable { call.block(timeout) }?.let { Exceptions.unwrap(it).message }

    /** The stored pointers of [host]. The store is read, so the index cannot hide a write. */
    private fun pointersIn(stores: PersistenceServiceBeans<Long, String>, host: Key<Long>): List<PointerMessage<Long>> =
        stores.messagePersistence().all().collectList().block(timeout)!!
            .filterIsInstance<PointerMessage<Long>>()
            .filter { it.key.dest == host.id }
}
