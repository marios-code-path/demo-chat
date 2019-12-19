package com.demo.chat.test

import com.demo.chat.codec.Codec
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

class UUIDCodec : Codec<String, UUID> {
    override fun decode(record: String): UUID = UUID.fromString(record)
}

class CodecTests : TestBase() {

    @Test
    fun `should encode generic methods`() {
        Assertions
                .assertThat(UUIDCodec().decode(UUID.randomUUID().toString()))
                .isNotNull()
    }
}