package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.BuildReason
import com.demo.chat.index.lucene.storage.Damage
import com.demo.chat.index.lucene.storage.IndexHeader
import com.demo.chat.index.lucene.storage.StartKind
import com.demo.chat.index.lucene.storage.StartOutcome
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.CorruptIndexException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

class IndexHeaderTests {

    @Test
    fun `a header survives its user data`() {
        val header = IndexHeader.current("user", "long", "18", StandardAnalyzer())
        assertThat(IndexHeader.read(header.userData())).isEqualTo(header)
        assertThat(header.userData().keys).containsExactlyInAnyOrder(
            "chat.format", "chat.lucene", "chat.analyzer", "chat.keyType", "chat.nodeId", "chat.index"
        )
    }

    @Test
    fun `user data with a missing key reads as no header`() {
        assertThat(IndexHeader.read(mapOf("chat.format" to "1"))).isNull()
    }

    @Test
    fun `the log lines name the outcome`() {
        assertThat(StartOutcome(StartKind.REUSED, null, 42, 0).logLine("user"))
            .isEqualTo("lucene index user: reused, 42 entries compared, 0 written")
        assertThat(StartOutcome(StartKind.BUILT, BuildReason.MISMATCH, 3, 42).logLine("user"))
            .isEqualTo("lucene index user: built, reason=MISMATCH, 42 entries written")
    }

    @Test
    fun `damage and failure are separate classes`() {
        assertThat(Damage.isDamage(CorruptIndexException("x", "y"))).isTrue()
        assertThat(Damage.isDamage(EOFException())).isTrue()
        assertThat(Damage.isDamage(NoSuchFileException("_0.cfs"))).isTrue()
        assertThat(Damage.isDamage(AccessDeniedException("/root"))).isFalse()
        assertThat(Damage.isDamage(java.io.IOException("No space left on device"))).isFalse()
    }
}
