package com.demo.chat.controller.config

import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.config.KeyServiceBeans
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class KeyControllersConfiguration {
    @Controller
    @MessageMapping("key")
    class KeyController<T>(factory: KeyServiceBeans<T>) :
            KeyServiceController<T>(factory.keyService())
}