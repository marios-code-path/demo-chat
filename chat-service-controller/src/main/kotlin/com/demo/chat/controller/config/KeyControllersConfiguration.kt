package com.demo.chat.controller.config

import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.config.KeyServiceBeans
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class KeyControllersConfiguration {
    @Controller
    @MessageMapping("key")
    @ConditionalOnProperty(prefix = "app.service.core.api", name = ["key"])
    class KeyController<T>(factory: KeyServiceBeans<T>) :
            KeyServiceController<T>(factory.keyService())
}