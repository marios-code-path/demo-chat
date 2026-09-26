package com.demo.chat.test.knownkey

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.ChatIdentity
import com.demo.chat.domain.knownkey.RootKeys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * A missing root key must name what the caller asked for. An empty set and a
 * partial set are different faults: the first means that loading never ran,
 * and the second is refused when it is loaded. See `CHAT-avduuqwp`.
 */
class RootKeysTests {

    @Test
    fun `an empty set says that the root keys are not loaded`() {
        assertThatThrownBy { RootKeys<Long>().of(ChatDomain.USER) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("User")
            .hasMessageContaining("not loaded")
    }

    @Test
    fun `a partial domain set is refused, and the refusal names the missing domains`() {
        assertThatThrownBy { RootKeys<Long>().loadDomains(mapOf(ChatDomain.USER to Key.funKey(1L))) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("MESSAGE")
            .hasMessageContaining("FRANKING_TAG")
    }

    @Test
    fun `a complete set returns each domain root`() {
        val rootKeys = RootKeys<Long>().apply { loadDomains(complete()) }

        ChatDomain.entries.forEach { assertThat(rootKeys.of(it)).isEqualTo(complete()[it]) }
    }

    @Test
    fun `a domain root id answers its domain, and a user identity answers none`() {
        val rootKeys = RootKeys<Long>().apply {
            loadDomains(complete())
            loadIdentities(Key.funKey(900L), Key.funKey(901L))
        }

        assertThat(rootKeys.domainOfRoot(complete().getValue(ChatDomain.MESSAGE).id)).isEqualTo(ChatDomain.MESSAGE)
        assertThat(rootKeys.domainOfRoot(900L)).isNull()
        assertThat(rootKeys.domainOfRoot(901L)).isNull()
    }

    @Test
    fun `the identities are users, not domains`() {
        val rootKeys = RootKeys<Long>().apply {
            loadDomains(complete())
            loadIdentities(Key.funKey(900L), Key.funKey(901L))
        }

        assertThat(rootKeys.admin()).isEqualTo(Key.funKey(900L))
        assertThat(rootKeys.anon()).isEqualTo(Key.funKey(901L))
        assertThat(rootKeys.identity(ChatIdentity.ANON)).isEqualTo(Key.funKey(901L))
        assertThat(rootKeys.domains().values).doesNotContain(Key.funKey(900L), Key.funKey(901L))
    }

    @Test
    fun `an identity that is not loaded is named`() {
        assertThatThrownBy { RootKeys<Long>().anon() }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("Anon")
    }

    @Test
    fun `a configuration name resolves to a domain root or an identity, and nothing else`() {
        val rootKeys = RootKeys<Long>().apply {
            loadDomains(complete())
            loadIdentities(Key.funKey(900L), Key.funKey(901L))
        }

        assertThat(rootKeys.byName("MessageTopic")).isEqualTo(complete()[ChatDomain.MESSAGE_TOPIC])
        assertThat(rootKeys.byName("Admin")).isEqualTo(Key.funKey(900L))
        assertThat(rootKeys.byName("KeyCredential")).isNull()
        assertThat(rootKeys.byName("user")).isNull()
    }

    private fun complete(): Map<ChatDomain, Key<Long>> =
        ChatDomain.entries.associateWith { Key.funKey(100L + it.ordinal) }
}
