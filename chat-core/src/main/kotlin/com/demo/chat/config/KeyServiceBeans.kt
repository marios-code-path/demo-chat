package com.demo.chat.config

import com.demo.chat.service.IKeyService

interface KeyServiceBeans<T> {
    fun keyService(): IKeyService<T>
}