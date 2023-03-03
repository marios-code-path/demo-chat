package com.demo.chat.config.controller.core

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.core.KeyServiceController
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Configuration
class KeyControllersConfiguration {
    @Controller
    @MessageMapping("key")
    @ConditionalOnProperty(prefix = "app.controller", name = ["key"])
    //@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    class KeyController<T>(factory: KeyServiceBeans<T>) :
            KeyServiceController<T>(factory.keyService())
}