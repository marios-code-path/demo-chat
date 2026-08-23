package com.demo.chat.test.knownkey

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * A missing root key used to surface as a bare NullPointerException from
 * `keyMap[domain]!!`, naming neither the domain requested nor the map's
 * contents. Those two facts are what separate "initialization never ran" from
 * "this one domain was never registered" — identical symptoms, different
 * faults. These tests pin both messages.
 */
class RootKeysTests {

    @Test
    fun `an empty map says initialization never ran`() {
        val rootKeys = RootKeys<Long>()

        assertThatThrownBy { rootKeys.getRootKey(Anon::class.java) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("Anon")
            .hasMessageContaining("no root keys are initialized at all")
    }

    @Test
    fun `a populated map names what it does hold`() {
        val rootKeys = RootKeys<Long>()
        rootKeys.addRootKey(User::class.java, Key.funKey(1L))
        rootKeys.addRootKey("Topic", Key.funKey(2L))

        assertThatThrownBy { rootKeys.getRootKey(Anon::class.java) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("Anon")
            .hasMessageContaining("Known root keys: Topic, User")
    }

    @Test
    fun `both overloads agree`() {
        val rootKeys = RootKeys<Long>()
        rootKeys.addRootKey(User::class.java, Key.funKey(7L))

        assertThat(rootKeys.getRootKey(User::class.java).id)
            .isEqualTo(rootKeys.getRootKey("User").id)
            .isEqualTo(7L)
    }

    @Test
    fun `a present key is returned rather than thrown for`() {
        val rootKeys = RootKeys<Long>()
        rootKeys.addRootKey(Anon::class.java, Key.funKey(42L))

        assertThat(rootKeys.getRootKey(Anon::class.java).id).isEqualTo(42L)
    }
}
