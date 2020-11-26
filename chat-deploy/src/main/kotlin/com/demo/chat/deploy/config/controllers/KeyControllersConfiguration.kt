package com.demo.chat.deploy.config.controllers

import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.service.IKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


open class KeyControllersConfiguration {

    @ConditionalOnProperty(prefix = "app.service", name = ["key"])
    @Controller
    @MessageMapping("key")
    class KeyController<T>(keyService: IKeyService<T>) : KeyServiceController<T>(keyService)
}