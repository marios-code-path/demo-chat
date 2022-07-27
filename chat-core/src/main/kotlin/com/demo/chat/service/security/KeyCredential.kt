package com.demo.chat.service.security

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
data class KeyCredential<T>(override val key: Key<T>, override val data: String) : KeyDataPair<T, String>