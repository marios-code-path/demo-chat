package com.demo.chat.test.init

import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.TopicCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
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
}
