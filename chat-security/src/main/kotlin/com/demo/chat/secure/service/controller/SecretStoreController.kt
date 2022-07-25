package com.demo.chat.secure.service.controller

import com.demo.chat.service.security.SecretsStore

open class SecretStoreController<T>(private val that: SecretsStore<T>) : SecretsStoreMapping<T>, SecretsStore<T> by that