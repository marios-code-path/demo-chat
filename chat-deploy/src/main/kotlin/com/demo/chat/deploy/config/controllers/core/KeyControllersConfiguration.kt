package com.demo.chat.deploy.config.controllers.core

import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.deploy.config.core.KeyServiceFactory
import com.demo.chat.service.IKeyService
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*

open class KeyControllersConfiguration {
    @Controller
    @MessageMapping("key")
    class KeyController<T>(factory: KeyServiceFactory<T>) :
            KeyServiceController<T>(factory.keyService())
}