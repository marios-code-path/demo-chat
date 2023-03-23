package com.demo.chat.service.init

import com.demo.chat.deploy.KnownRootKeys
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import java.util.function.Supplier

class RootKeysSupplier<T>(
    private val keyService: IKeyService<T>,
) : Supplier<Map<String, Key<T>>> {
    override fun get(): Map<String, Key<T>> {
        val keyMap = mutableMapOf<String, Key<T>>()

        KnownRootKeys.knownRootKeys.forEach { k ->
            keyMap[k.simpleName] = keyService.key(k).block()!!
        }

        return keyMap
    }
}