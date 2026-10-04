package com.demo.chat.test.init

import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.TopicCommands
import com.demo.chat.shell.commands.UnknownRoomException
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired

@Tag("integration")
class LongShellTopicCommandsTests : ShellTopicCommandsTests<Long>()

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("integration")
open class ShellTopicCommandsTests<T : Any> : ShellIntegrationTestBase() {

    @Autowired
    private lateinit var topicCommands: TopicCommands<T>

    @Autowired
    private lateinit var loginCommands: LoginCommands<T>

    @Test
    @Order(1)
    fun `should add topic and list at least one`() {
        topicCommands.addTopic("_", "test")
        topicCommands.addTopic("_", "testABC")


        val rawTopics = topicCommands.showTopics()
        val topics = rawTopics?.split("\n")

        Assertions.assertThat(topics)
            .isNotNull
            .hasSizeGreaterThan(1)

        Assertions
            .assertThat(rawTopics)
            .isNotBlank
            .contains("test")
    }

    @Test
    @Order(2)
    fun `should join and get members for at least 1 `() {
        // An anonymous caller cannot join a room, so this test logs in. CHAT-mfveaecc.
        loginAsAdmin()
        topicCommands.addTopic("_", "test2")
        topicCommands.join("_", "test2")

        val rawMembers = topicCommands.listMembers("test2")
        val members = rawMembers?.split("\n")

        Assertions.assertThat(members)
            .isNotNull
            .hasSizeGreaterThan(0)

        Assertions
            .assertThat(rawMembers)
            .isNotBlank
    }

    /**
     * **A room command takes a room id as well as a name.** One `--topic`
     * value carries either. See `CHAT-scoizkpm`.
     */
    @Test
    @Order(3)
    fun `join and list members take the room id`() {
        loginAsAdmin()
        topicCommands.addTopic("_", "byIdRoom")
        val topicId = topicCommands.topicByName("_", "byIdRoom")!!.substringBefore(" | ")

        topicCommands.join("_", topicId)

        Assertions.assertThat(topicCommands.listMembers(topicId)).isNotBlank
        Assertions.assertThat(topicCommands.listMembers(topicId)).isEqualTo(topicCommands.listMembers("byIdRoom"))
    }

    @Test
    @Order(4)
    fun `a room command on an unknown room reports one message`() {
        val error = org.junit.jupiter.api.Assertions.assertThrows(UnknownRoomException::class.java) {
            topicCommands.listMembers("noSuchRoom")
        }
        Assertions.assertThat(error.message).isEqualTo("No room has the name or the id noSuchRoom.")
    }

   // @Test
   // @Order(3)
    fun `should join and get memberOf `() {
        topicCommands.addTopic("_", "test3")
        topicCommands.join("_", "test3")

        val rawMembers = topicCommands.memberOf("_")
        val members = rawMembers?.split("\n")

        Assertions.assertThat(members)
            .isNotNull
            .hasSizeGreaterThan(0)

        Assertions
            .assertThat(rawMembers)
            .isNotBlank
    }

   // @Test
   // @Order(4)
    fun `should join leave having no memberships for user`() {
        topicCommands.addTopic("_", "test4")
        topicCommands.join("_", "test4")


        val rawMembers = topicCommands.memberOf("_")
        val members = rawMembers?.split("\n")
        val memberOfCount = members?.size

        topicCommands.leave("_", "test4")

        val newMemberOfCount = topicCommands.memberOf("_")?.split("\n")?.size

        Assertions.assertThat(newMemberOfCount)
            .isNotNull
            .isLessThan(memberOfCount)
    }

    @Test
    @Order(5)
    fun `should join leave and have no members in room`() {
        loginAsAdmin()
        topicCommands.addTopic("_", "test5")

        topicCommands.join("_", "test5")
        topicCommands.leave("_", "test5")

        val rawMembers = topicCommands.listMembers("test5")
        val members = rawMembers?.split("\n")

        Assertions.assertThat(members)
            .isNullOrEmpty()
    }

    @Test
    @Order(6)
    fun `adding a topic with an existing name fails`() {
        // Names are unique. The first add creates the room; the second must
        // fail with the duplicate error, not create a second room. fp issue
        // CHAT-qktlglfa.
        topicCommands.addTopic("_", "dupRoom")

        val error = org.junit.jupiter.api.Assertions.assertThrows(ApplicationErrorException::class.java) {
            topicCommands.addTopic("_", "dupRoom")
        }

        Assertions.assertThat(error.message)
            .contains("Object already exists")
    }

    /**
     * **An anonymous caller cannot join a room.** The owner decided this on
     * 2026-10-02. No login holds here, so the shell sends the `Anon` key as the
     * member. See `CHAT-mfveaecc`.
     */
    @Test
    @Order(7)
    fun `an anonymous caller cannot join a room`() {
        loginAsAdmin()
        topicCommands.addTopic("_", "anonJoinRoom")
        ShellStateConfiguration.clearLogin()

        val error = org.junit.jupiter.api.Assertions.assertThrows(ApplicationErrorException::class.java) {
            topicCommands.join("_", "anonJoinRoom")
        }

        Assertions.assertThat(error.message)
            .contains("An anonymous caller cannot join a room.")
    }

    private fun loginAsAdmin() {
        loginCommands.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
    }
}
