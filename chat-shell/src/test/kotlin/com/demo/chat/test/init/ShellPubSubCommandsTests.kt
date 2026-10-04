package com.demo.chat.test.init

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

    /**
     * **`messages` lists what the room stores, by name and by id.** The owner
     * holds the room, so the `SUBSCRIBE` check allows the read. See
     * `CHAT-rghaeqsa`.
     */
    @Test
    @Order(7)
    fun `messages lists the stored messages of a room the caller may read`() {
        loginAsAdmin()
        topicCommands.addTopic("_", "pubsubM")
        pubSubCommands.send(topic = "pubsubM", userName = "_", messageText = "first stored")
        pubSubCommands.send(topic = "pubsubM", userName = "_", messageText = "second stored")
        val topicId = topicCommands.topicByName("_", "pubsubM")!!.substringBefore(" | ")

        val byName = pubSubCommands.messages("pubsubM")
        val byId = pubSubCommands.messages(topicId)

        Assertions.assertThat(byName.lines()).hasSize(2)
        Assertions.assertThat(byName).contains("first stored", "second stored")
        Assertions.assertThat(byId.lines()).containsExactlyInAnyOrderElementsOf(byName.lines())
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
