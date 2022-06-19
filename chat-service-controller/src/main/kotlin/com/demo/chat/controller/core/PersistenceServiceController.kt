package com.demo.chat.controller.core

import com.demo.chat.controller.core.mapping.PersistenceStoreMapping
import com.demo.chat.service.PersistenceStore

open class PersistenceServiceController<T, E>(private val that: PersistenceStore<T, E>) : PersistenceStoreMapping<T, E>,
    PersistenceStore<T, E> by that