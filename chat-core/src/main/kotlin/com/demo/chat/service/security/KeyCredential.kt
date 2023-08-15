package com.demo.chat.service.security

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import org.springframework.boot.context.properties.bind.ConstructorBinding

data class KeyCredential<T> @ConstructorBinding constructor(override val key: Key<T>, override val data: String) : KeyValuePair<T, String>
data class ContextCredential<T> @ConstructorBinding constructor(override val key: Key<T>, val type: String, override val data: String) : KeyValuePair<T, String>