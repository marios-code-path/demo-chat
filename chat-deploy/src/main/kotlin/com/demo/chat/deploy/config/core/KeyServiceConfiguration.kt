package com.demo.chat.deploy.config.core

import com.demo.chat.service.IKeyService

interface KeyServiceConfiguration<T> {
    fun keyService(): IKeyService<T>
}