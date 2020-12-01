package com.demo.chat.deploy.config.core

import com.demo.chat.service.IKeyService

interface KeyServiceFactory<T> {
    fun keyService(): IKeyService<T>
}