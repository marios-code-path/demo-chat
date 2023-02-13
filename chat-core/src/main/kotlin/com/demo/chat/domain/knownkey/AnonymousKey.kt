package com.demo.chat.domain.knownkey

import com.demo.chat.domain.Key

data class AnonymousKey<T>(override val id: T) : Key<T> {
    override val empty: Boolean = false
}