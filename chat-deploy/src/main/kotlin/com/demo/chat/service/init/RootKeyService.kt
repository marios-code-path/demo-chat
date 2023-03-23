package com.demo.chat.service.init

import com.demo.chat.deploy.KnownRootKeys.Companion.knownRootKeys
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyValueStore
import com.fasterxml.jackson.databind.ObjectMapper

class RootKeyService(
    private val kvStore: KeyValueStore<String, String>,
    private val mapper: ObjectMapper,
    val dataKey: String
) {

    fun <T> consumeRootKeys(rootKeys: RootKeys<T>) = kvStore
        .get(Key.funKey(dataKey))
        .doOnNext {
            val map = mapper.readValue(it.data, Map::class.java)
            rootKeys.merge(map as Map<String, Key<T>>)
        }.block()

    fun<T> publishRootKeys(rootKeys: RootKeys<T>) = kvStore
        .add(
            KeyDataPair.create(
                Key.funKey(dataKey),
                mapper.writeValueAsString(rootKeys.getMapOfKeyMap())
            )
        ).block()

    fun<T> rootKeySummary(rootKeys: RootKeys<T>): String {
        val sb = StringBuilder()

        sb.append("Root Keys: \n")
        for (rootKey in knownRootKeys) {
            if (rootKeys.hasRootKey(rootKey))
                sb.append("${rootKey.simpleName}=${rootKeys.getRootKey(rootKey)}\n")
        }

        return sb.toString()
    }

}