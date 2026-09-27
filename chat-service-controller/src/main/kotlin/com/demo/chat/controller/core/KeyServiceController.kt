package com.demo.chat.controller.core

import com.demo.chat.domain.TypeUtil

import com.demo.chat.controller.core.mapping.IKeyServiceMapping
import com.demo.chat.service.core.IKeyService

open class KeyServiceController<T>(private val that: IKeyService<T>, private val typeUtil: TypeUtil<T>) : IKeyServiceMapping<T>,
    IKeyService<T> by that {
    override fun typeUtil(): TypeUtil<T> = typeUtil
}