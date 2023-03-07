package com.demo.chat.config

import com.demo.chat.service.security.SecretsStore

interface SecretsStoreBeans<T> {
    fun secretsStore(): SecretsStore<T>
}