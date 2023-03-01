package com.demo.chat.domain.knownkey

import com.demo.chat.domain.Key

class RootKeys<T> {
    private val rootKeys: MutableMap<String, Key<T>> = mutableMapOf()

    fun <S> getRootKey(domain: Class<S>) =  rootKeys[domain.name]!!
    fun <S> addRootKey(domain: Class<S>, key: Key<T>) = rootKeys.put(domain.name, key)
    fun <S> removeRootKey(domain: Class<S>) = rootKeys.remove(domain.name)
    fun <S> hasRootKey(domain: Class<S>) = rootKeys.containsKey(domain.name)
    fun hasRootKey(key: Key<T>) = rootKeys.containsValue(key)
    fun <S> hasRootKey(domain: Class<S>, key: Key<T>) = rootKeys[domain.name] == key
}