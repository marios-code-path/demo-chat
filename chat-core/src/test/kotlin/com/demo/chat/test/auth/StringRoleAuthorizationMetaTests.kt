package com.demo.chat.test.auth

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.Key
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class StringRoleAuthorizationMetaTests {

    @Test
    fun `should create`() {
        Assertions
            .assertThat(StringRoleAuthorizationMetadata(TestKeys.key(1024L), TestKeys.key(1L), TestKeys.key(2L), "TEST"))
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }

    @Test
    fun `should create with strange input`() {
        Assertions
            .assertThat(StringRoleAuthorizationMetadata(
                TestKeys.key(1024L),
                TestKeys.key(Long.MAX_VALUE),
                TestKeys.key(Long.MIN_VALUE),
                Random.nextLong().toString()))
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }
}