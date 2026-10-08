package com.demo.chat.service.core

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys

/**
 * Assigns a key with no I/O. Decision 1 of the spec. Admission uses it, and
 * the `P` handler registers the key later through [IKeyService.register].
 * The generator carries the node id, so the key-type rules still hold.
 */
class KeyAllocator<T>(private val ids: IKeyGenerator<T>, private val rootKeys: RootKeys<T>) {
    fun allocate(domain: ChatDomain): Key<T> = Key.of(ids.nextId(), rootKeys.of(domain).id)
}
