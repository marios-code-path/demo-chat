package com.demo.chat.config.controller.core

import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.controller.core.mapping.SecretsStoreMapping
import com.demo.chat.service.security.SecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


//@Controller
@ConditionalOnProperty(prefix = "app.controller", name = ["secrets"])
//@MessageMapping("secrets")
class SecretsStoreControllerConfiguration<T>(beans: SecretsStoreBeans<T>) : SecretsStoreMapping<T>,
    SecretsStore<T> by beans.secretsStore()