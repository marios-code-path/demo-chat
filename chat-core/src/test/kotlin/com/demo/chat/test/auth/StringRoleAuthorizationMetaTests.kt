package com.demo.chat.test.auth

import com.demo.chat.domain.Key
import com.demo.chat.service.StringRoleAuthorizationMetadata
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class StringRoleAuthorizationMetaTests {

    @Test
    fun `should create`() {
        Assertions
            .assertThat(StringRoleAuthorizationMetadata(Key.funKey(1024L), Key.funKey(1L), Key.funKey(2L), "TEST"))
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }

    @Test
    fun `should create with strange input`() {
        Assertions
            .assertThat(StringRoleAuthorizationMetadata(
                Key.funKey(1024L),
                Key.funKey(Long.MAX_VALUE),
                Key.funKey(Long.MIN_VALUE),
                Random.nextLong().toString()))
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }
}