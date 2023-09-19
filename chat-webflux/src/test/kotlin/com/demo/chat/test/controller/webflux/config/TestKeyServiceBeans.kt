package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.service.core.IKeyService
import org.mockito.BDDMockito

class TestKeyServiceBeans<T> : KeyServiceBeans<T> {

    val keyService: IKeyService<T> = BDDMockito.mock(IKeyService::class.java) as IKeyService<T>

    override fun keyService(): IKeyService<T> = keyService
}