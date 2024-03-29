package com.demo.chat.domain.knownkey

import com.demo.chat.domain.Key

data class Anon<T>(override val id: T, override val empty: Boolean = false) : Key<T>