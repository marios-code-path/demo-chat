package com.demo.chat.test.init

import com.demo.chat.shell.commands.TopicCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired

@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ShellTopicCommandsTests<T> : ShellIntegrationTestBase() {

    @Autowired
    private lateinit var topicCommands: TopicCommands<T>

    @Test
    @Order(1)
    fun `should add topic and list at least one`() {
        topicCommands.addTopic("_", "test")
        topicCommands.addTopic("_", "test2")


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
        topicCommands.join("_", "test")

        val rawMembers = topicCommands.listMembers("test")
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
        topicCommands.join("_", "test")

        val rawMembers = topicCommands.memberOf("_")
        val members = rawMembers?.split("\n")

        Assertions.assertThat(members)
            .isNotNull
            .hasSizeGreaterThan(0)

        Assertions
            .assertThat(rawMembers)
            .isNotBlank
    }

}