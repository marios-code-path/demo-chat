package com.demo.chat.service.init

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyValueStore
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory


open class KVRootKey {
    var id: Any = 0
    var empty: Boolean = false
}

class RootKeyService<T>(
    private val kvStore: KeyValueStore<String, String>,
    private val typeUtil: TypeUtil<T>,
    val dataKey: String
) {

    fun consumeRootKeys(rootKeys: RootKeys<T>) = kvStore
        .get(Key.funKey(dataKey))
        .doOnNext {
            val mapper = ObjectMapper(YAMLFactory())
            val reference = object : TypeReference<Map<String, KVRootKey>>() {}
            val result = mapper.readValue(
                it.data,
                reference
            )

            result.keys.forEach { key ->
                if (result.containsKey(key)) {
                    val domain = result[key]!!

                    rootKeys.addRootKey(key, Key.funKey(typeUtil.assignFrom(domain.id)))
                }
            }

        }.block()

    fun <T> publishRootKeys(rootKeys: RootKeys<T>) = kvStore
        .add(
            KeyDataPair.create(
                Key.funKey(dataKey),
                ObjectMapper(YAMLFactory()).writeValueAsString(rootKeys.getMapOfKeyMap())
            )
        ).block()
}