package com.demo.chat.controller.core

import com.demo.chat.controller.core.mapping.KeyValueStoreMapping
import com.demo.chat.service.core.KeyValueStore

open class KeyValueStoreController<T>(private val that: KeyValueStore<T, Any>) : KeyValueStoreMapping<T>,
        KeyValueStore<T, Any> by that