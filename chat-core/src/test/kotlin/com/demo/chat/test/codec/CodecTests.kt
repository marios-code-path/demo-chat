package com.demo.chat.test.codec

import com.demo.chat.codec.Codec
import com.demo.chat.test.TestBase
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*

class UUIDCodec : Codec<String, UUID> {
    override fun decode(record: String): UUID = UUID.fromString(record)
}

@ExtendWith(SpringExtension::class)
class CodecTests : TestBase() {

    @Test
    fun `should encode Generic`() {
        val codec: Codec<String,Int> = mock()

        whenever(codec.decode(any()))
                .thenReturn(4)

        Assertions
                .assertThat(codec.decode("TEST"))
                .isNotNull()
                .isEqualTo(4)
    }

    @Test
    fun `should encode UUID methods`() {
        Assertions
                .assertThat(UUIDCodec().decode(UUID.randomUUID().toString()))
                .isNotNull()
    }
}