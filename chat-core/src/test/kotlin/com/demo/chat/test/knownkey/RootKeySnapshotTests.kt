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
        loadDomains(ChatDomain.entries.associateWith { Key.root(100L + it.ordinal) })
        loadIdentities(Key.of(900L, 100L + ChatDomain.USER.ordinal), Key.of(901L, 100L + ChatDomain.USER.ordinal))
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

    @Test
    fun `a malformed domain id is refused and nothing loads`() {
        val full = RootKeySnapshot.of(source, "long", TypeUtil.LongUtil)
        val bad = full.copy(domains = full.domains + (ChatDomain.MESSAGE.wireName to "not-a-number"))
        val target = RootKeys<Long>()

        assertThatThrownBy { bad.load(target, "long", TypeUtil.LongUtil) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("Message")
        assertThat(target.domains()).isEmpty()
        assertThat(target.identities()).isEmpty()
    }

    @Test
    fun `a malformed Admin id is refused and nothing loads`() {
        val bad = RootKeySnapshot.of(source, "long", TypeUtil.LongUtil).copy(admin = "9223372036854775808")
        val target = RootKeys<Long>()

        assertThatThrownBy { bad.load(target, "long", TypeUtil.LongUtil) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("Admin")
        assertThat(target.domains()).isEmpty()
        assertThat(target.identities()).isEmpty()
    }

    @Test
    fun `a malformed Anon id is refused and nothing loads`() {
        val bad = RootKeySnapshot.of(source, "long", TypeUtil.LongUtil).copy(anon = "anon")
        val target = RootKeys<Long>()

        assertThatThrownBy { bad.load(target, "long", TypeUtil.LongUtil) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("Anon")
        assertThat(target.domains()).isEmpty()
        assertThat(target.identities()).isEmpty()
    }

    @Test
    fun `a snapshot that uses one id for two roots is refused`() {
        val full = RootKeySnapshot.of(source, "long", TypeUtil.LongUtil)
        val shared = full.copy(anon = full.domains.getValue(ChatDomain.USER.wireName))

        assertThatThrownBy { shared.load(RootKeys<Long>(), "long", TypeUtil.LongUtil) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("one id for two roots")
    }
}
