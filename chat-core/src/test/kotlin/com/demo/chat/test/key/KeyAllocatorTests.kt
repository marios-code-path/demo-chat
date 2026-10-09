package com.demo.chat.test.key

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.core.KeyAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KeyAllocatorTests {
    private val roots = FakeKeyServices.longRoots()
    private val registry = FakeKeyServices.long(roots)

    @Test
    fun `an allocated key carries the root of its domain`() {
        val key = KeyAllocator(LongKeyGenerator(1), roots).allocate(ChatDomain.MESSAGE)
        assertThat(key.root).isEqualTo(roots.of(ChatDomain.MESSAGE).id)
    }

    @Test
    fun `allocation writes nothing to the key registry`() {
        val key = KeyAllocator(LongKeyGenerator(1), roots).allocate(ChatDomain.MESSAGE)
        assertThat(registry.rootOf(key.id).block()).isNull()
    }

    @Test
    fun `two allocations give two ids`() {
        val allocator = KeyAllocator(LongKeyGenerator(1), roots)
        assertThat(allocator.allocate(ChatDomain.MESSAGE).id).isNotEqualTo(allocator.allocate(ChatDomain.MESSAGE).id)
    }
}
