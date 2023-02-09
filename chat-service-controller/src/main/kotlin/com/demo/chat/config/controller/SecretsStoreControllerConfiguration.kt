package com.demo.chat.config.controller

import com.demo.chat.controller.core.mapping.SecretsStoreMapping
import com.demo.chat.service.security.SecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Controller
@ConditionalOnProperty(prefix = "app.service.core", name = ["secrets"])
@MessageMapping("secrets")
class SecretsStoreControllerConfiguration<T>(private val that: SecretsStore<T>) : SecretsStoreMapping<T>, SecretsStore<T> by that