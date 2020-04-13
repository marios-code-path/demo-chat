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

class StringUUIDCodec : Codec<String, UUID> {
    override fun decode(record: String): UUID = UUID.fromString(record)
}

@ExtendWith(SpringExtension::class)
class CodecTests : TestBase() {
    @Test
    fun `even better`() {
            val i = 0
            val n = i.let {
                it + 1
            }
            println(i)
            println(n)
        }

    @Test
    fun `way better than javascript`() {
        val n = 1
        val v = n.let {
            val tmp = n + 1
            println("$this , $tmp")
            tmp
        }
    }

    @Test
    fun `better than javascript`() {
        var n: Int? = 1
        var v = n
        v.apply {
            n = n?.plus(1)
            v = n
            println("$this , $n")
        }
    }

    @Test
    fun `should encode Generic`() {
        val codec: Codec<String, Int> = mock()

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
                .assertThat(StringUUIDCodec().decode(UUID.randomUUID().toString()))
                .isNotNull()
    }
}