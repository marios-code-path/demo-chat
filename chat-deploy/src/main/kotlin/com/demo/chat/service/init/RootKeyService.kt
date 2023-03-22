package com.demo.chat.service.init

import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.deploy.KnownRootKeys.Companion.knownRootKeys
import com.fasterxml.jackson.databind.ObjectMapper

class RootKeyService<T>(
    private val keyService: IKeyService<T>,
    private val kvStore: KeyValueStore<String, String>,
    private val mapper: ObjectMapper,
    val key: String
) {

    fun createDomainKeys():Map<String, Key<T>> {
        val keyMap = mutableMapOf<String, Key<T>>()

        knownRootKeys.forEach { k ->
            keyMap[k.simpleName] = keyService.key(k).block()!!
        }

        return keyMap
    }

    fun consumeRootKeys(rootKeys: RootKeys<T>) = kvStore
        .get(Key.funKey(key))
        .doOnNext {
            val map = mapper.readValue(it.data, Map::class.java)
            rootKeys.merge(map as Map<String, Key<T>>)
        }.block()

    fun publishRootKeys(rootKeys: RootKeys<T>) = kvStore
        .add(
            KeyDataPair.create(
                Key.funKey(key),
                mapper.writeValueAsString(rootKeys.getMapOfKeyMap())
            )
        ).block()

    fun rootKeySummary(rootKeys: RootKeys<T>): String {
        val sb = StringBuilder()

        sb.append("Root Keys: \n")
        for (rootKey in knownRootKeys) {
            if (rootKeys.hasRootKey(rootKey))
                sb.append("${rootKey.simpleName}=${rootKeys.getRootKey(rootKey)}\n")
        }

        return sb.toString()
    }

}