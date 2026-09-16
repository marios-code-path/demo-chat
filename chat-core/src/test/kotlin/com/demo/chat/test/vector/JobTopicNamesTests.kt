package com.demo.chat.test.vector

import com.demo.chat.service.vector.JobTopicNames
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class JobTopicNamesTests {
    private val at = Instant.parse("2026-09-11T12:00:00Z")

    @Test
    fun `a job topic name carries the node id and the key type`() {
        val name = JobTopicNames.nameFor(7, "long", at, "abc")

        Assertions.assertThat(name).startsWith(JobTopicNames.PREFIX)
        Assertions.assertThat(JobTopicNames.isJobTopic(name)).isTrue()
        Assertions.assertThat(JobTopicNames.matches(name, 7, "long")).isTrue()
    }

    @Test
    fun `another node or another key type does not match`() {
        val name = JobTopicNames.nameFor(7, "long", at, "abc")

        Assertions.assertThat(JobTopicNames.matches(name, 8, "long")).isFalse()
        Assertions.assertThat(JobTopicNames.matches(name, 7, "uuid")).isFalse()
    }

    @Test
    fun `a user room name is not a job topic`() {
        Assertions.assertThat(JobTopicNames.isJobTopic("general")).isFalse()
    }

    @Test
    fun `two jobs of one incarnation take two names`() {
        val first = JobTopicNames.nameFor(7, "long", at, "abc")
        val second = JobTopicNames.nameFor(7, "long", at.plusSeconds(1), "abc")

        Assertions.assertThat(first).isNotEqualTo(second)
    }
}
