package com.demo.chat.controller.webflux

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.webflux.core.mapping.KeyValueStoreRestMapping
import com.demo.chat.service.core.KeyValueStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/persist/kv")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class KeyValueStoreRestController<T, V>(s: PersistenceServiceBeans<T, V>) : KeyValueStoreRestMapping<T>,
    KeyValueStore<T, Any> by s.keyValuePersistence()