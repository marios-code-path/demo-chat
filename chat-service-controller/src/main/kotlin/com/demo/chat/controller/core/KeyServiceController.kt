package com.demo.chat.controller.core

import com.demo.chat.config.controller.rbac.IKeyServiceSecurity
import com.demo.chat.controller.core.mapping.IKeyServiceMapping
import com.demo.chat.service.core.IKeyService

open class KeyServiceController<T>(private val that: IKeyService<T>): IKeyServiceMapping<T>,
    IKeyServiceSecurity<T>, IKeyService<T> by that