package com.demo.chat.domain.knownkey

import com.demo.chat.domain.Key
import java.util.concurrent.ConcurrentHashMap

class RootKeys<T> {
    private val rootKeys: MutableMap<String, Key<T>> = ConcurrentHashMap(20)

    fun keys(): Map<String, Key<T>> = rootKeys.toMap()
    fun <S> getRootKey(domain: Class<S>): Key<T> = rootKeys[domain.simpleName]!!
    fun getRootKey(domain: String) = rootKeys[domain]!!
    fun <S> addRootKey(domain: Class<S>, key: Key<T>) = rootKeys.put(domain.simpleName, key)
    fun <S> hasRootKey(domain: Class<S>) = rootKeys.containsKey(domain.simpleName)
    fun hasKey(key: String) = rootKeys.containsKey(key)
    fun addRootKey(domain: String, key: Key<T>) = rootKeys.put(domain, key)
    fun <S> hasRootKey(domain: Class<S>, key: Key<T>): Boolean {
        return if(hasRootKey(domain)) {
            getRootKey(domain).id == key.id
        }
        else {
            false
        }
    }
}