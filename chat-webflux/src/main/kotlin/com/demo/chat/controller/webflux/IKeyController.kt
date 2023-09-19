package com.demo.chat.controller.webflux

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.webflux.core.mapping.IKeyRestMapping
import com.demo.chat.service.core.IKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/key")
@ConditionalOnProperty(prefix = "app.controller", name = ["key"])
class IKeyRestController<T>(private val that: KeyServiceBeans<T>) : IKeyRestMapping<T>,
    IKeyService<T> by that.keyService()