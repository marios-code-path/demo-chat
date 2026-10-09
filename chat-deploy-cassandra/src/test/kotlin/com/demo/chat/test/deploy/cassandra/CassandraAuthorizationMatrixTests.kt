package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The authorization matrix of `docs/ANONYMOUS-AUTHORIZATION.md`, measured
 * against a Cassandra store and a Cassandra authorization index.
 *
 * **`AnonymousAuthorizationMatrixTests` replaces the store and the index with
 * maps.** Its rows describe a store that keeps every grant row. The Cassandra
 * index kept one row per target until `CHAT-rmxxtwtu`, so those rows were not
 * known to hold on this backend. This class measures them here.
 *
 * The grants come from `userinit.yml`, which the launch surface loads. The
 * stack is the production one: `CoreAuthorizationService`, `AuthSummarizer`,
 * `AuthMetadataAccessBroker` and `SpringSecurityAccessBrokerService`. Only the
 * key type differs from the shipped deployment, which is `long` here.
 *
 * Node id 24 belongs to this class. See docs/NODEID-CLAIM.md.
 */
@Tag("integration")
class CassandraAuthorizationMatrixTests : CassandraContainerBase() {

    private val timeout = Duration.ofSeconds(10)

    /**
     * The launch surface, written as command line arguments. See
     * CassandraClaimBootTests for why a command line argument is necessary.
     * The initial users are on, because a grant read puts the `Anon` identity
     * in its actor set, and that identity loads with the initial users.
     */
    private fun launchArguments(): Array<String> = arrayOf(
        "spring.config.location=classpath:/application.yml",
        "spring.config.additional-location=classpath:/config/logging.yml," +
            "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.key.type=long",
        "app.nodeid=24",
        "app.users.create=true",
        "app.service.core.key=cassandra",
        "app.service.core.persistence=cassandra",
        "app.service.core.index=cassandra",
        "app.service.core.pubsub=memory",
        "app.service.core.secrets=cassandra",
        "app.service.composite",
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
    ).map { "--$it" }.toTypedArray()

    private fun start(): ConfigurableApplicationContext =
        SpringApplicationBuilder(ChatApp::class.java)
            .web(WebApplicationType.NONE)
            .run(*launchArguments())

    /**
     * **The operations answer the same on Cassandra as on a map store.**
     *
     * `getRoom`, `whoami`, `messageById` and `listRooms` allow. `getRoom` and
     * `messageById` read one object, and since `CHAT-rfzsnbco` the check reads
     * the domain root of that object beside it. `userinit.yml` names both roots:
     * `{User, MessageTopic, GET}` and `{Anon, Message, GET}`. `whoami` and
     * `listRooms` name a domain root already.
     *
     * `addRoom`, `send` and `addUser` deny. No shipped row grants `NEW` on
     * `MessageTopic` or on `User`, and the `{User, Message, SEND}` row names
     * the `Message` root, which is not the root of a room.
     */
    @Test
    fun `the shipped grants answer the same matrix on cassandra`() {
        start().use { context ->
            @Suppress("UNCHECKED_CAST")
            val composite = context.getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
            @Suppress("UNCHECKED_CAST")
            val access = context.getBean(SpringSecurityAccessBrokerService::class.java)
                as SpringSecurityAccessBrokerService<Long>

            val caller = composite.userService()
                .addUser(UserCreateRequest("matrix", "matrixuser", "http://u"))
                .block(timeout)!!
            val room = composite.topicService()
                .addRoom(ByStringRequest("matrixroom"))
                .block(timeout)!!

            // A real message of this deployment. Before `CHAT-xcmpudyb`, the
            // send failed on this backend, so this row used a minted key.
            // A composite send binds the sender to the authenticated user, so
            // the call carries the caller.
            val message = composite.messageService()
                .send(MessageSendRequest("matrix message", caller.id, room.id))
                .contextWrite(
                    ReactiveSecurityContextHolder.withAuthentication(
                        UsernamePasswordAuthenticationToken(
                            ChatUserDetails(User.create(caller, "matrixuser", "matrixuser", "http://u"), listOf()), "n/a", listOf(),
                        )
                    )
                )
                .block(timeout)!!

            // `addRoom` allows since 2026-10-01. The owner decided on that date
            // that every user may add a room, and the shipped
            // `{User, MessageTopic, NEW}` row reaches every caller that holds
            // an identity. `addUser` still denies, because creating a user is
            // the work of an `Admin`.
            val expected = linkedMapOf(
                "addRoom MessageTopic NEW" to true,
                "send room SEND" to false,
                "getRoom GET" to true,
                "whoami User FIND" to true,
                "messageById GET" to true,
                "listRooms MessageTopic GET_ALL" to true,
                "addUser User NEW" to false,
                "deleteRoom MessageTopic REM" to false
            )

            // The caller is a real user of this deployment, and the room and
            // the message are real objects of it. Each row reads a stored
            // grant, so the store and the index both answer.
            assertThat(matrix(access, room, message, anonymous()))
                .describedAs("an anonymous caller")
                .isEqualTo(expected)
            assertThat(matrix(access, room, message, authenticated(caller)))
                .describedAs("an authenticated caller")
                .isEqualTo(expected)
            assertThat(matrix(access, room, message, unauthenticated(caller)).values)
                .describedAs("an unauthenticated caller")
                .allMatch { !it }
            assertThat(matrix(access, room, message, unsupported()).values)
                .describedAs("an unsupported principal")
                .allMatch { !it }
            assertThat(matrix(access, room, message, null).values)
                .describedAs("no context")
                .allMatch { !it }
        }
    }

    /**
     * The room and the message are read from the store, so the rows also prove
     * that a grant read joins the index to the store on this backend.
     */
    private fun matrix(
        access: SpringSecurityAccessBrokerService<Long>,
        room: Key<Long>,
        message: Key<Long>,
        context: SecurityContext?
    ): Map<String, Boolean> = linkedMapOf(
        "addRoom MessageTopic NEW" to answer({ it.hasAccessToDomain("MessageTopic", "NEW") }, access, context),
        "send room SEND" to answer({ it.hasAccessTo(room, "SEND") }, access, context),
        "getRoom GET" to answer({ it.hasAccessTo(room, "GET") }, access, context),
        "whoami User FIND" to answer({ it.hasAccessToDomain("User", "FIND") }, access, context),
        "messageById GET" to answer({ it.hasAccessTo(message, "GET") }, access, context),
        "listRooms MessageTopic GET_ALL" to answer({ it.hasAccessToDomain("MessageTopic", "GET_ALL") }, access, context),
        "addUser User NEW" to answer({ it.hasAccessToDomain("User", "NEW") }, access, context),
        "deleteRoom MessageTopic REM" to answer({ it.hasAccessTo(room, "REM") }, access, context)
    )

    private fun answer(
        call: (SpringSecurityAccessBrokerService<Long>) -> Mono<Boolean>,
        access: SpringSecurityAccessBrokerService<Long>,
        context: SecurityContext?
    ): Boolean {
        val chain = call(access)
        val withContext = if (context == null) {
            chain
        } else {
            chain.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
        }
        return withContext.block(timeout) ?: false
    }

    private fun anonymous() = SecurityContextImpl(
        AnonymousAuthenticationToken(
            "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        )
    )

    private fun authenticated(caller: Key<Long>) =
        SecurityContextImpl(UsernamePasswordAuthenticationToken(details(caller), "secret", listOf()))

    private fun unauthenticated(caller: Key<Long>) =
        SecurityContextImpl(UsernamePasswordAuthenticationToken.unauthenticated(details(caller), "secret"))

    private fun unsupported() =
        SecurityContextImpl(UsernamePasswordAuthenticationToken("a-plain-string", "secret", listOf()))

    private fun details(caller: Key<Long>) =
        ChatUserDetails(User.create(caller, "u", "handle", "http://u"), listOf())
}
