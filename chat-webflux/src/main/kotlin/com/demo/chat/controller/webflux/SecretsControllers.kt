package com.demo.chat.controller.webflux

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.controller.webflux.core.mapping.SecretsRestMapping
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.SecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/secrets")
@ConditionalOnProperty(prefix = "app.controller", name = ["secrets"])
class SecretsRestController<T>(private val that: SecretsStoreBeans<T>, private val keyService: KeyServiceBeans<T>) :
    SecretsRestMapping<T>,
    SecretsStore<T> by that.secretsStore() {
    override fun keyService(): IKeyService<T> = keyService.keyService()
}