package com.demo.chat.config

import com.demo.chat.service.core.IKeyService

interface KeyServiceBeans<T> {
    fun keyService(): IKeyService<T>
}