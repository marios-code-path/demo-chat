package com.demo.chat.test.knownkey

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeySnapshot
import com.demo.chat.domain.knownkey.RootKeys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The snapshot that a process without store access reads. See `CHAT-avduuqwp`. */
class RootKeySnapshotTests {

    private val source = RootKeys<Long>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.funKey(100L + it.ordinal) })
        loadIdentities(Key.funKey(900L), Key.funKey(901L))
    }

    @Test
    fun `a snapshot round trip loads every root and both identities`() {
        val target = RootKeys<Long>()
        RootKeySnapshot.of(source, "long", TypeUtil.LongUtil).load(target, "long", TypeUtil.LongUtil)

        assertThat(target.domains()).isEqualTo(source.domains())
        assertThat(target.admin()).isEqualTo(source.admin())
        assertThat(target.anon()).isEqualTo(source.anon())
    }

    @Test
    fun `a snapshot with another key type is refused`() {
        val snapshot = RootKeySnapshot.of(source, "long", TypeUtil.LongUtil)

        assertThatThrownBy { snapshot.load(RootKeys<Long>(), "uuid", TypeUtil.LongUtil) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("long")
            .hasMessageContaining("uuid")
    }

    @Test
    fun `a snapshot that does not name every domain is refused`() {
        val full = RootKeySnapshot.of(source, "long", TypeUtil.LongUtil)
        val partial = full.copy(domains = full.domains - ChatDomain.FRANKING_TAG.wireName)

        assertThatThrownBy { partial.load(RootKeys<Long>(), "long", TypeUtil.LongUtil) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("FRANKING_TAG")
    }

    @Test
    fun `a snapshot that names an unknown domain is refused`() {
        val full = RootKeySnapshot.of(source, "long", TypeUtil.LongUtil)
        val stale = full.copy(domains = full.domains + ("KeyDataPair" to "7"))

        assertThatThrownBy { stale.load(RootKeys<Long>(), "long", TypeUtil.LongUtil) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("KeyDataPair")
    }
}
