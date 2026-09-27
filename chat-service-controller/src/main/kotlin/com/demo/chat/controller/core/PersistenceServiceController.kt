package com.demo.chat.controller.core

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.controller.core.mapping.PersistenceStoreMapping
import com.demo.chat.service.core.PersistenceStore

open class PersistenceServiceController<T, E : Any>(
    private val that: PersistenceStore<T, E>,
    private val verifier: KeyVerifier<T>,
) : PersistenceStoreMapping<T, E>,
    PersistenceStore<T, E> by that {
    override fun verifier(): KeyVerifier<T> = verifier
}