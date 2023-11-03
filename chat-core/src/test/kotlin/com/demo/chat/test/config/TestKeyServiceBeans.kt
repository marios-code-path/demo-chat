package com.demo.chat.test.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.service.core.IKeyService
import org.mockito.BDDMockito

open class TestKeyServiceBeans<T> : KeyServiceBeans<T> {

    val mockKeyService: IKeyService<T> = BDDMockito.mock(IKeyService::class.java)
            as? IKeyService<T> ?: throw ClassCastException("Unable to cast mock to IKeyService<T>")


    override fun keyService(): IKeyService<T> = mockKeyService
}