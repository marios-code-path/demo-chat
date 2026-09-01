package com.demo.chat.test.init

import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.TopicCommands
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
open class ShellPubSubCommandsTests<T> : ShellIntegrationTestBase() {

    @Autowired
    private lateinit var pubSubCommands: PubSubCommands<T>

    @Autowired
    private lateinit var topicCommands: TopicCommands<T>

    @Test
    @Order(1)
    fun `send by topic name uses the looked-up room id`() {
        // The defect: the topicName branch sent with the topicId option,
        // which still held its default underscore. Parsing the underscore
        // as a key threw NumberFormatException before any request left the
        // client. fp issue B8, CHAT-qonhhtuq.
        topicCommands.addTopic("_", "pubsubA")

        assertDoesNotThrow {
            pubSubCommands.send(topicName = "pubsubA", topicId = "_",
                userName = "_", messageText = "hello by name")
        }
    }

    @Test
    @Order(2)
    fun `send by topic id reaches the room`() {
        val raw = topicCommands.topicByName("_", "pubsubA")
        Assertions.assertThat(raw).isNotBlank

        val topicId = raw!!.substringBefore(" | ")

        assertDoesNotThrow {
            pubSubCommands.send(topicName = "_", topicId = topicId,
                userName = "_", messageText = "hello by id")
        }
    }

    @Test
    @Order(3)
    fun `send to an unknown topic name reports not found`() {
        // Before the fallback fix, an unknown name died in single() as a
        // raw NoSuchElementException. fp issue CHAT-fplhtycq.
        val error = assertThrows(ApplicationErrorException::class.java) {
            pubSubCommands.send(topicName = "no-such-room-${System.nanoTime()}",
                topicId = "_", userName = "_", messageText = "hello nowhere")
        }
        Assertions.assertThat(error.message).contains("Object not Found")
    }

    @Test
    @Order(4)
    fun `hangup disposes the stored listener and forgets it`() {
        val raw = topicCommands.topicByName("_", "pubsubA")
        val topicId = raw!!.substringBefore(" | ")

        pubSubCommands.listen(topicId)

        val stored = ShellStateConfiguration.listeners[topicId]
        assertTrue(stored != null && !stored.isDisposed,
            "listen must store a live Disposable for the topic id")

        pubSubCommands.hangup(topicId)

        assertTrue(stored!!.isDisposed, "hangup must dispose the listener")
        assertFalse(ShellStateConfiguration.listeners.containsKey(topicId),
            "hangup must remove the entry from the listener map")
    }

    @Test
    @Order(5)
    fun `hangup on a topic never listened to is a no-op`() {
        assertDoesNotThrow {
            pubSubCommands.hangup("999999999999")
        }
    }
}
