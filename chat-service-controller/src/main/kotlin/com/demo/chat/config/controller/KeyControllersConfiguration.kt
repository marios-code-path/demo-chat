package com.demo.chat.config.controller

import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.config.KeyServiceBeans
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Configuration
open class KeyControllersConfiguration {
    @Controller
    @MessageMapping("key")
    @ConditionalOnProperty(prefix = "app.controller", name = ["key"])
    class KeyController<T>(factory: KeyServiceBeans<T>) :
            KeyServiceController<T>(factory.keyService())
}