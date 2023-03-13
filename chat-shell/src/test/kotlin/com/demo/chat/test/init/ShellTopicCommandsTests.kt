package com.demo.chat.test.init

import com.demo.chat.shell.commands.TopicCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired

class LongShellTopicCommandsTests : ShellTopicCommandsTests<Long>()

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
open class ShellTopicCommandsTests<T> : ShellIntegrationTestBase() {

    @Autowired
    private lateinit var topicCommands: TopicCommands<T>

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

    @Test
    @Order(3)
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

    @Test
    @Order(4)
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
        topicCommands.addTopic("_", "test5")

        topicCommands.join("_", "test5")
        topicCommands.leave("_", "test5")

        val rawMembers = topicCommands.listMembers("test5")
        val members = rawMembers?.split("\n")

        Assertions.assertThat(members)
            .isNullOrEmpty()
    }
}