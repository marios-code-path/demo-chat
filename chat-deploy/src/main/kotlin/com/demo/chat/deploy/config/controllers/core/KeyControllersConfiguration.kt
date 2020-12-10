package com.demo.chat.deploy.config.controllers.core

import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.deploy.config.core.KeyServiceConfiguration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class KeyControllersConfiguration {
    @Controller
    @MessageMapping("key")
    class KeyController<T>(factory: KeyServiceConfiguration<T>) :
            KeyServiceController<T>(factory.keyService())
}