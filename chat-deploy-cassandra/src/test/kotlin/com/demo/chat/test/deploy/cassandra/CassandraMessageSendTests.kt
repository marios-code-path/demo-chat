package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.UserCreateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.time.Duration

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
 * Node ids 25 and 26 belong to this class. See docs/NODEID-CLAIM.md.
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

            val sent = composite.messageService()
                .send(MessageSendRequest("hello $keyType", sender.id, room.id))
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
}
