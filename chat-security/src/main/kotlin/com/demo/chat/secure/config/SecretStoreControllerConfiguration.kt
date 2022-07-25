package com.demo.chat.secure.config

import com.demo.chat.secure.service.controller.SecretStoreController
import com.demo.chat.service.security.SecretsStore
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class SecretStoreControllerConfiguration {

    @Controller
    @MessageMapping("secrets")
    class SecretsController<T>(s: SecretsStore<T>) : SecretStoreController<T>(s)
}