package com.demo.chat.test.auth

import com.demo.chat.service.StringRoleAuthorizationMetadata
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class StringRoleAuthorizationMetaTests {

    @Test
    fun `should create`() {
        Assertions
            .assertThat(StringRoleAuthorizationMetadata(1L, 2L, "TEST"))
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }

    @Test
    fun `should create with strange input`() {
        Assertions
            .assertThat(StringRoleAuthorizationMetadata(
                Long.MAX_VALUE,
                Long.MIN_VALUE,
                Random.nextLong().toString()))
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }
}