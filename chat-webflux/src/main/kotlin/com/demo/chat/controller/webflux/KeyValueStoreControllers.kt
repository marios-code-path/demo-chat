package com.demo.chat.controller.webflux

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.webflux.core.mapping.KeyValueStoreRestMapping
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.KeyValueStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/persist/kv")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class KeyValueStoreRestController<T, V>(
    s: PersistenceServiceBeans<T, V>,
    private val typeUtil: TypeUtil<T>
) : KeyValueStoreRestMapping<T>,
    KeyValueStore<T, Any> by s.keyValuePersistence() {

    override fun typeUtil(): TypeUtil<T> = typeUtil
}