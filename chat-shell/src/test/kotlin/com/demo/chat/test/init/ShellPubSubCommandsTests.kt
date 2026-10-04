package com.demo.chat.test.init

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.shell.commands.UserCommands
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.TopicCommands
import com.demo.chat.shell.commands.UnknownRoomException
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired

@Tag("integration")
class LongPubSubCommandsTests : ShellPubSubCommandsTests<Long>()

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("integration")
open class ShellPubSubCommandsTests<T : Any> : ShellIntegrationTestBase() {

    @Autowired
    private lateinit var pubSubCommands: PubSubCommands<T>

    @Autowired
    private lateinit var topicCommands: TopicCommands<T>

    @Autowired
    private lateinit var loginCommands: LoginCommands<T>

    @Test
    @Order(1)
    fun `send by topic name uses the looked-up room id`() {
        // The defect: the topicName branch sent with the topicId option,
        // which still held its default underscore. Parsing the underscore
        // as a key threw NumberFormatException before any request left the
        // client. fp issue B8, CHAT-qonhhtuq.
        loginAsAdmin()
        topicCommands.addTopic("_", "pubsubA")

        assertDoesNotThrow {
            pubSubCommands.send(topic = "pubsubA", userName = "_", messageText = "hello by name")
        }
    }

    @Test
    @Order(2)
    fun `send by topic id reaches the room`() {
        loginAsAdmin()
        val raw = topicCommands.topicByName("_", "pubsubA")
        Assertions.assertThat(raw).isNotBlank

        val topicId = raw!!.substringBefore(" | ")

        assertDoesNotThrow {
            pubSubCommands.send(topic = topicId, userName = "_", messageText = "hello by id")
        }
    }

    @Autowired
    private lateinit var userCommands: UserCommands<T>

    @Autowired
    private lateinit var compositeServices: CompositeServiceBeans<T, String>

    @Autowired
    private lateinit var typeUtil: TypeUtil<T>

    private fun loginAsAdmin() {
        loginCommands.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
    }

    @Test
    @Order(3)
    fun `send to an unknown topic reports one message`() {
        // Before the fallback fix, an unknown name died in single() as a
        // raw NoSuchElementException. fp issue CHAT-fplhtycq. The shell now
        // reads a name or an id, and a miss on both reads names the value.
        // CHAT-scoizkpm.
        val topic = "no-such-room-${System.nanoTime()}"
        val error = assertThrows(UnknownRoomException::class.java) {
            pubSubCommands.send(topic = topic, userName = "_", messageText = "hello nowhere")
        }
        Assertions.assertThat(error.message).isEqualTo("No room has the name or the id $topic.")
    }

    @Test
    @Order(4)
    fun `hangup disposes the stored listener and forgets it`() {
        val raw = topicCommands.topicByName("_", "pubsubA")
        val topicId = raw!!.substringBefore(" | ")

        Assertions.assertThat(pubSubCommands.listen(topicId)).isEqualTo(topicId)

        val stored = ShellStateConfiguration.listeners[topicId]
        assertTrue(stored != null && !stored.isDisposed,
            "listen must store a live Disposable for the topic id")

        Assertions.assertThat(pubSubCommands.hangup(topicId)).isEqualTo(topicId)

        assertTrue(stored!!.isDisposed, "hangup must dispose the listener")
        assertFalse(ShellStateConfiguration.listeners.containsKey(topicId),
            "hangup must remove the entry from the listener map")
    }

    @Test
    @Order(5)
    fun `hangup on a room never listened to is a no-op`() {
        assertDoesNotThrow {
            pubSubCommands.hangup("pubsubA")
        }
    }

    /**
     * **A hangup resolves its value as a room.** An unknown id names itself.
     * The server refuses an id that the registry does not hold, so the message
     * names both causes. See `CHAT-scoizkpm`.
     */
    @Test
    @Order(6)
    fun `hangup on an unknown room reports one message`() {
        val error = assertThrows(UnknownRoomException::class.java) {
            pubSubCommands.hangup("999999999999")
        }
        Assertions.assertThat(error.message)
            .isEqualTo("No room has the name or the id 999999999999, or you may not read that room.")
    }

    /** This test reads three stored messages from two senders through the running server. */
    @Test
    @Order(7)
    fun `messages displays stored time and sender handles in order`() {
        loginAsAdmin()
        val room = "pubsubM"
        topicCommands.addTopic("_", room)
        val handle = "log-reader-${System.nanoTime()}"
        val userId = userCommands.addUser("Log reader", handle, "")!!
        val password = java.util.UUID.randomUUID().toString()
        userCommands.passwd(userId, password)
        topicCommands.join(userId, room)
        pubSubCommands.send(topic = room, userName = "_", messageText = "first stored")
        loginCommands.login(handle, password)
        pubSubCommands.send(topic = room, userName = "_", messageText = "second stored")
        loginAsAdmin()
        pubSubCommands.send(topic = room, userName = "_", messageText = "third stored\ncontinued")
        val topicId = topicCommands.topicByName("_", room)!!.substringBefore(" | ")
        val request = ByIdRequest(typeUtil.fromString(topicId))
        val stored = compositeServices.messageService().listMessages(request).collectList().block()!!
            .sortedBy { it.key.timestamp }
        val originalTimes = stored.associate { it.key.id to it.key.timestamp }
        Assertions.assertThat(stored.map { it.data }).containsExactly("first stored", "second stored", "third stored\ncontinued")
        // Host and container clocks can differ. Compare stored times across reads instead.
        Thread.sleep(1100)
        val reread = compositeServices.messageService().listMessages(request).collectList().block()!!
        reread.forEach {
            Assertions.assertThat(it.key.timestamp).isEqualTo(originalTimes.getValue(it.key.id))
        }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneId.systemDefault())
        val handles = listOf(ShellDeploymentAccount.ADMIN_HANDLE, handle, ShellDeploymentAccount.ADMIN_HANDLE)
        val expected = stored.mapIndexed { index, message ->
            val prefix = "${formatter.format(message.key.timestamp)} | ${handles[index]} | "
            prefix + message.data.lines().joinToString("\n" + " ".repeat(prefix.length))
        }

        Assertions.assertThat(pubSubCommands.messages(room)).isEqualTo(expected.joinToString("\n"))
        Assertions.assertThat(pubSubCommands.messages(topicId)).isEqualTo(expected.joinToString("\n"))
        Assertions.assertThat(pubSubCommands.messages(room, 2)).isEqualTo(expected.takeLast(2).joinToString("\n"))
        Assertions.assertThat(pubSubCommands.messages(room, 1)).isEqualTo(expected.last())
    }

    @Test
    @Order(8)
    fun `messages names a room that stores no message`() {
        loginAsAdmin()
        topicCommands.addTopic("_", "pubsubEmpty")

        Assertions.assertThat(pubSubCommands.messages("pubsubEmpty")).isEqualTo("No messages in pubsubEmpty.")
    }

    /** **A caller that may not listen may not list.** The read reports `Access Denied`. */
    @Test
    @Order(9)
    fun `a refused messages read reports Access Denied`() {
        loginAsAdmin()
        topicCommands.addTopic("_", "pubsubClosed")
        pubSubCommands.send(topic = "pubsubClosed", userName = "_", messageText = "for members")
        ShellStateConfiguration.clearLogin()

        Assertions.assertThatThrownBy { pubSubCommands.messages("pubsubClosed") }
            .describedAs("the refusal of SUBSCRIBE for a caller that is not a member")
            .isInstanceOf(CoreAuthorizationRefusal::class.java)
            .hasMessageContaining("Access Denied")
    }
}
