package com.demo.chat.config

import com.demo.chat.service.security.SecretsStore

interface SecurityServiceBeans<T> {
    fun secretsStore(): SecretsStore<T>
}