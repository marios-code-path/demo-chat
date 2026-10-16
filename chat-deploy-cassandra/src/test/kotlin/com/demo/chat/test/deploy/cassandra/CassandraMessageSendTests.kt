package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.command.ImportedMessageId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.Exceptions
import java.time.Duration
import java.util.UUID

/**
 * A message send on a Cassandra deployment, for each key type.
 *
 * **Before `CHAT-xcmpudyb`, no test sent a message on this backend.** The long
 * keyspace declared `msg_id TIMESTAMP`, and the first write failed with
 * `Codec not found for requested operation: [TIMESTAMP <-> java.lang.Long]`.
 *
 * The send runs through the composite message service, as a controller calls
 * it. The message is read back by its id from the store, and by its room
 * through the index.
 *
 * Node ids 25 through 28 belong to this class. See docs/NODEID-CLAIM.md.
 */
@Tag("integration")
class CassandraMessageSendTests : CassandraContainerBase() {

    private val timeout = Duration.ofSeconds(10)

    /** The launch surface of `CassandraAuthorizationMatrixTests`, with the key type and the node id. */
    private fun start(keyType: String, nodeId: Int): ConfigurableApplicationContext =
        SpringApplicationBuilder(ChatApp::class.java)
            .web(WebApplicationType.NONE)
            .run(*arrayOf(
                "spring.config.location=classpath:/application.yml",
                "spring.config.additional-location=classpath:/config/logging.yml," +
                    "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
                "server.port=0",
                "spring.rsocket.server.port=0",
                "app.key.type=$keyType",
                "app.nodeid=$nodeId",
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

    private fun sendAndRead(keyType: String, nodeId: Int) {
        start(keyType, nodeId).use { context ->
            @Suppress("UNCHECKED_CAST")
            val composite = context.getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Any, String>

            val sender = composite.userService()
                .addUser(UserCreateRequest("sender", "sender$keyType", "http://u"))
                .block(timeout)!!
            val room = composite.topicService()
                .addRoom(ByStringRequest("sendroom$keyType"))
                .block(timeout)!!

            // A composite send binds the sender to the authenticated user, so
            // the call carries the sender.
            val sent = composite.messageService()
                .send(MessageSendRequest("hello $keyType", sender.id, room.id))
                .contextWrite(
                    ReactiveSecurityContextHolder.withAuthentication(
                        UsernamePasswordAuthenticationToken(
                            ChatUserDetails(User.create(sender, "sender", "sender$keyType", "http://u"), listOf()), "n/a", listOf(),
                        )
                    )
                )
                .block(timeout)!!

            val byId = composite.messageService().messageById(ByIdRequest(sent.id)).block(timeout)!!
            assertThat(byId.data).isEqualTo("hello $keyType")
            assertThat(byId.key.from).isEqualTo(sender.id)
            assertThat(byId.key.dest).isEqualTo(room.id)

            val byRoom = composite.messageService().listMessages(ByIdRequest(room.id))
                .collectList().block(timeout)!!
            assertThat(byRoom.map { it.key.id }).containsExactly(sent.id)
            assertThat(byRoom.single().data).isEqualTo("hello $keyType")
            // The store and the index keep one time for one message.
            assertThat(byRoom.single().key.timestamp).isEqualTo(byId.key.timestamp)
        }
    }

    @Test
    fun `a message sent on a long cassandra deployment reads back by id and by room`() =
        sendAndRead("long", 25)

    @Test
    fun `a message sent on a uuid cassandra deployment reads back by id and by room`() =
        sendAndRead("uuid", 26)

    /** The composite, two senders, a room, and an Admin context on one deployment. */
    private inner class ImportScene(keyType: String, context: ConfigurableApplicationContext) {
        @Suppress("UNCHECKED_CAST")
        val composite = context.getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Any, String>
        @Suppress("UNCHECKED_CAST")
        private val rootKeys = context.getBean(RootKeys::class.java) as RootKeys<Any>
        val firstSender = composite.userService()
            .addUser(UserCreateRequest("first $keyType", "first$keyType", "http://u"))
            .block(timeout)!!
        val secondSender = composite.userService()
            .addUser(UserCreateRequest("second $keyType", "second$keyType", "http://u"))
            .block(timeout)!!
        val room = composite.topicService()
            .addRoom(ByStringRequest("importroom$keyType"))
            .block(timeout)!!
        private val admin = UsernamePasswordAuthenticationToken(
            ChatUserDetails(
                User.create(rootKeys.admin(), "Admin", "Admin", "http://u"),
                listOf("ROLE_ADMIN"),
            ),
            "n/a",
            listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )
        val adminContext = ReactiveSecurityContextHolder.withAuthentication(admin)

        /** A room-scoped source id, the form that the importer sends. See `CHAT-bwzjstoh`. */
        fun sourceId(turn: String) = "${room.id}:$turn"

        fun import(sender: Key<Any>, text: String, time: java.time.Instant, turn: String) =
            composite.messageService()
                .importMessage(MessageImportRequest(text, sender.id, room.id, time, sourceId(turn)))
                .contextWrite(adminContext)

        fun history() = composite.messageService().listMessages(ByIdRequest(room.id))
            .contextWrite(adminContext)
            .collectList()
            .block(timeout)!!
    }

    private val early = java.time.Instant.parse("2026-10-08T10:00:00.001Z")
    private val middle = java.time.Instant.parse("2026-10-08T10:00:01.001Z")
    private val late = java.time.Instant.parse("2026-10-08T10:00:02.001Z")

    // Each import derives its id from the source id, so each message reads back by that id. CHAT-bwzjstoh.
    @Test
    fun `three imported messages are ordered on a uuid cassandra deployment and read back by their derived ids`() {
        start("uuid", 28).use { context ->
            val scene = ImportScene("uuid", context)
            val turns = mapOf("late" to LATE, "early" to EARLY, "middle" to MIDDLE)

            scene.import(scene.secondSender, "late", late, LATE).block(timeout)!!
            scene.import(scene.firstSender, "early", early, EARLY).block(timeout)!!
            scene.import(scene.firstSender, "middle", middle, MIDDLE).block(timeout)!!

            val messages = scene.history()
            assertThat(messages.map { it.data }).containsExactly("early", "middle", "late")
            assertThat(messages.map { it.key.from })
                .containsExactly(scene.firstSender.id, scene.firstSender.id, scene.secondSender.id)
            turns.forEach { (text, turn) ->
                val derived = ImportedMessageId.derive(scene.sourceId(turn), scene.room.id as UUID)
                val read = scene.composite.messageService().messageById(ByIdRequest(derived))
                    .contextWrite(scene.adminContext)
                    .block(timeout)!!
                assertThat(read.data).describedAs("the message read by the derived id of $text").isEqualTo(text)
            }
        }
    }

    // A Long key cannot hold a name-based UUID, so a long deployment refuses each import. CHAT-bwzjstoh.
    @Test
    fun `an import on a long cassandra deployment is refused and stores nothing`() {
        start("long", 27).use { context ->
            val scene = ImportScene("long", context)

            val error = Exceptions.unwrap(
                runCatching { scene.import(scene.firstSender, "early", early, EARLY).block(timeout) }.exceptionOrNull()
                    ?: error("The import did not fail.")
            )

            assertThat(error.message).contains("The key type Long cannot hold an imported id")
            assertThat(scene.history()).isEmpty()
        }
    }

    private companion object {
        const val EARLY = "1d0f0a52-6a3e-4b8e-9a51-0e2f3c4b5a61"
        const val MIDDLE = "2e1a1b63-7b4f-4c9f-8b62-1f3a4d5c6b72"
        const val LATE = "3f2b2c74-8c5a-4dae-9c73-2a4b5e6d7c83"
    }
}
