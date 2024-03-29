package com.demo.chat.domain.knownkey

import com.demo.chat.domain.Key
import java.util.concurrent.ConcurrentHashMap

class RootKeys<T> {

    private var keyMap: MutableMap<String, Key<T>> = ConcurrentHashMap(20)
        set(value) {
            field.clear()
            field.putAll(value)
        }
        get() = field

    fun merge(other: Map<String, Key<T>>) {
        keyMap.putAll(other)
    }
    fun getMapOfKeyMap(): Map<String, Key<T>> = keyMap.toMap()
    fun <S> getRootKey(domain: Class<S>): Key<T> = keyMap[domain.simpleName]!!
    fun getRootKey(domain: String) = keyMap[domain]!!
    fun <S> addRootKey(domain: Class<S>, key: Key<T>) = keyMap.put(domain.simpleName, key)
    fun <S> isRootKeyWithValue(domain: Class<S>) = keyMap.containsKey(domain.simpleName)
    fun hasKey(key: String) = keyMap.containsKey(key)
    fun <S> hasKey(domain: Class<S>) = keyMap.containsKey(domain.simpleName)
    fun addRootKey(domain: String, key: Key<T>) = keyMap.put(domain, key)
    fun <S> isRootKeyWithValue(domain: Class<S>, key: Key<T>): Boolean {
        return if(isRootKeyWithValue(domain)) {
            getRootKey(domain).id == key.id
        }
        else {
            false
        }
    }

    companion object {
        fun <T> rootKeySummary(rootKeys: RootKeys<T>): String {
            val sb = StringBuilder()

            sb.append("Root Keys: \n")
            for (rootKey in rootKeys.keyMap.keys) {
                sb.append("${rootKey}=${rootKeys.getRootKey(rootKey)}\n")
            }

            return sb.toString()
        }
    }
}