package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.service.security.SecretsStore
import org.mockito.BDDMockito

class TestSecretsStoreBeans<T> : SecretsStoreBeans<T> {

    val service = BDDMockito.mock(SecretsStore::class.java) as SecretsStore<T>

    override fun secretsStore(): SecretsStore<T> = service
}