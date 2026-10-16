package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.PointerMessage
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MessagePersistence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A message store keeps a pointer and reads it back as a pointer. See
 * `CHAT-erkoxybm`.
 *
 * A backend subclasses this once. [keys] mints under [rootKeys], and [store]
 * reads the same roots.
 */
@Disabled
abstract class PointerStoreTestBase<T : Any>(
    val store: MessagePersistence<T, String>,
    val keys: IKeyService<T>,
    val rootKeys: RootKeys<T>,
) {
    @Test
    fun `a pointer to a message survives a write and a read`() = roundTrip(ChatDomain.MESSAGE)

    @Test
    fun `a pointer to a topic survives a write and a read`() = roundTrip(ChatDomain.MESSAGE_TOPIC)

    /**
     * A removed pointer never reads back as a live record. The Cassandra
     * backend keeps the row and clears the flag. The memory and Redis backends
     * delete the row. A live answer here makes a removed pointer a valid
     * target, and the creation check then refuses it as a pointer. See
     * `CHAT-erkoxybm`.
     */
    @Test
    fun `a removed pointer does not read back as a live record`() {
        val written = pointerTo(ChatDomain.MESSAGE)
        store.add(written).block()
        val key = Key.of(written.key.id, written.key.root)
        assertThat(store.get(key).block()).describedAs("the pointer before the removal").isNotNull()

        store.rem(key).block()

        val after = store.get(key).block()
        assertThat(after?.record ?: false)
            .describedAs("the record flag of a removed pointer")
            .isFalse()
    }

    /** The pointer is the only stored form of a pointer. Its type carries the meaning. */
    private fun roundTrip(domain: ChatDomain) {
        val written = pointerTo(domain)
        store.add(written).block()

        val read = store.get(Key.of(written.key.id, written.key.root)).block()

        assertThat(read).describedAs("the stored pointer").isInstanceOf(PointerMessage::class.java)
        @Suppress("UNCHECKED_CAST")
        val pointer = read as PointerMessage<T>
        assertThat(pointer.target).describedAs("the target key").isEqualTo(written.target)
        assertThat(pointer.target.root).describedAs("the target root").isEqualTo(rootKeys.of(domain).id)
        assertThat(pointer.key.from).describedAs("the creator").isEqualTo(written.key.from)
        assertThat(pointer.key.dest).describedAs("the host room").isEqualTo(written.key.dest)
        assertThat(pointer.key.timestamp).describedAs("the time").isEqualTo(written.key.timestamp)
        assertThat(pointer.data).describedAs("the pointer data").isEmpty()
        assertThat(pointer.record).describedAs("the record flag").isTrue()
    }

    private fun pointerTo(domain: ChatDomain): PointerMessage<T> {
        val own = keys.key(ChatDomain.MESSAGE).block()!!
        val creator = keys.key(ChatDomain.USER).block()!!
        val host = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        val target = keys.key(domain).block()!!
        // Cassandra keeps milliseconds, so the time carries no finer part.
        val at = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        return PointerMessage(SimpleMessageKey(own.id, own.root, creator.id, host.id, at), Key.of(target.id, target.root))
    }
}
