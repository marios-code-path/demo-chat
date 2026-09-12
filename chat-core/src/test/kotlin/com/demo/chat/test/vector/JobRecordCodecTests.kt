package com.demo.chat.test.vector

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.JobRecord
import com.demo.chat.domain.Key
import com.demo.chat.service.vector.JobRecordCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * A job record travels as text, so the codec must read back what it wrote.
 */
class JobRecordCodecTests {
    private val mapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }

    private val codec = JobRecordCodec(mapper)

    private val record = JobRecord(
        key = Key.funKey(7L),
        jobKey = Key.funKey(500L),
        workerKey = Key.funKey(1000L),
        at = Instant.parse("2026-09-12T12:00:00Z"),
        message = "rebuild started",
        indexed = 3L,
    )

    @Test
    fun `a known version completes an encode and decode round trip`() {
        val decoded = codec.decode<Long>(codec.encode(record))

        Assertions.assertThat(decoded.key.id).isEqualTo(7L)
        Assertions.assertThat(decoded.jobKey.id).isEqualTo(500L)
        Assertions.assertThat(decoded.workerKey.id).isEqualTo(1000L)
        Assertions.assertThat(decoded.message).isEqualTo("rebuild started")
        Assertions.assertThat(decoded.at).isEqualTo(record.at)
        Assertions.assertThat(decoded.indexed).isEqualTo(3L)
    }

    // The version is checked before the payload binds. The payload here could
    // never bind, so a binding error would prove the check ran too late.
    @Test
    fun `an unknown version fails before the payload binds`() {
        val text = """{"version":99,"record":{"nonsense":true}}"""

        Assertions.assertThatThrownBy { codec.decode<Long>(text) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("99")
    }

    @Test
    fun `an envelope with no record fails`() {
        val text = """{"version":1}"""

        Assertions.assertThatThrownBy { codec.decode<Long>(text) }
            .isInstanceOf(ChatException::class.java)
    }
}
