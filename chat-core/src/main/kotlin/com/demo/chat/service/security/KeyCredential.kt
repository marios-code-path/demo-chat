package com.demo.chat.service.security

import com.demo.chat.domain.Key

data class KeyCredential<T>(val key: Key<T>, val credential: String)