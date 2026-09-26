package com.demo.chat.service.init

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeySnapshot
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.InitializingKVStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * This service writes and reads one `RootKeySnapshot` under [name] in a string
 * keyed store, such as Consul. The snapshot is JSON. See `CHAT-avduuqwp`.
 */
class RootKeyService<T>(
    private val kvStore: InitializingKVStore,
    private val typeUtil: TypeUtil<T>,
    val name: String,
    private val keyType: String,
) {
    private val mapper = jacksonObjectMapper()

    /** This method loads the stored snapshot into [rootKeys]. A missing or invalid snapshot fails. */
    fun consumeRootKeys(rootKeys: RootKeys<T>) {
        val json = kvStore.read(name).block()
            ?: throw ChatException("No root key snapshot is stored under '$name'.")
        mapper.readValue<RootKeySnapshot>(json).load(rootKeys, keyType, typeUtil)
    }

    fun publishRootKeys(rootKeys: RootKeys<T>) {
        kvStore.write(name, mapper.writeValueAsString(RootKeySnapshot.of(rootKeys, keyType, typeUtil))).block()
    }
}
