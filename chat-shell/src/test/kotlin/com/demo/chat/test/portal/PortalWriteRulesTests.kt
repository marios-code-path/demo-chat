package com.demo.chat.test.portal

import com.demo.chat.shell.portal.PortalErrorCode
import com.demo.chat.shell.portal.PortalProtocolException
import com.demo.chat.shell.portal.PortalWriteRules
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PortalWriteRulesTests {
    @Test
    fun `a key refuses the pipe and the backslash`() {
        refuse { PortalWriteRules.requireKey("a|b") }
        refuse { PortalWriteRules.requireKey("a\\b") }
    }

    @Test
    fun `a key refuses an empty value and a value past 64 characters`() {
        refuse { PortalWriteRules.requireKey("") }
        refuse { PortalWriteRules.requireKey(null) }
        refuse { PortalWriteRules.requireKey("a".repeat(65)) }
        assertThat(PortalWriteRules.requireKey("a".repeat(64))).hasSize(64)
    }

    @Test
    fun `a key refuses a control character and a surrounding space`() {
        refuse { PortalWriteRules.requireKey("a\tb") }
        refuse { PortalWriteRules.requireKey(" a") }
        refuse { PortalWriteRules.requireKey("a ") }
    }

    @Test
    fun `a key refuses the archive dash`() {
        refuse { PortalWriteRules.requireKey("-") }
    }

    @Test
    fun `a short name refuses an uppercase letter`() {
        refuse { PortalWriteRules.requireShort("Snap") }
        refuse { PortalWriteRules.requireShort("snap_1") }
        assertThat(PortalWriteRules.requireShort("snap-1")).isEqualTo("snap-1")
    }

    @Test
    fun `a title refuses the pipe and the empty value`() {
        refuse { PortalWriteRules.requireTitle("a|b") }
        refuse { PortalWriteRules.requireTitle("") }
        assertThat(PortalWriteRules.requireTitle("Snapshot one")).isEqualTo("Snapshot one")
    }

    @Test
    fun `a title accepts 256 characters and refuses 257`() {
        assertThat(PortalWriteRules.requireTitle("a".repeat(256))).hasSize(256)
        refuse { PortalWriteRules.requireTitle("a".repeat(257)) }
    }

    @Test
    fun `a text of 3072 bytes is accepted and 3073 is refused`() {
        assertThat(PortalWriteRules.requireText("a".repeat(3072))).hasSize(3072)
        refuse { PortalWriteRules.requireText("a".repeat(3073)) }
    }

    @Test
    fun `a text is measured in UTF-8 bytes and refuses a blank value`() {
        assertThat(PortalWriteRules.requireText("é".repeat(1536))).hasSize(1536)
        refuse { PortalWriteRules.requireText("é".repeat(1537)) }
        refuse { PortalWriteRules.requireText("   ") }
        refuse { PortalWriteRules.requireText(null) }
    }

    @Test
    fun `the request id underscore is refused`() {
        refuse { PortalWriteRules.requireRequestId("_") }
        refuse { PortalWriteRules.requireRequestId("a b") }
        assertThat(PortalWriteRules.requireRequestId("r-1:a.b_c")).isEqualTo("r-1:a.b_c")
    }

    @Test
    fun `a request id accepts 128 characters and refuses 129`() {
        assertThat(PortalWriteRules.requireRequestId("r".repeat(128))).hasSize(128)
        refuse { PortalWriteRules.requireRequestId("r".repeat(129)) }
    }

    @Test
    fun `an absent request id stays absent`() {
        assertThat(PortalWriteRules.requireRequestId(null)).isNull()
    }

    private fun refuse(block: () -> Unit) {
        assertThatThrownBy(block).isInstanceOfSatisfying(PortalProtocolException::class.java) {
            assertThat(it.code).isEqualTo(PortalErrorCode.INVALID_INPUT)
        }
    }
}
