package com.demo.chat.test.domain

import com.demo.chat.domain.TopicMembership
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test


class MembershipTests {
    @Test
    fun `Should create`() {
        Assertions
                .assertThat(TopicMembership.create("Key", "Topic1", "Topic2"))
                .isNotNull
                .hasNoNullFieldsOrProperties()
    }
}