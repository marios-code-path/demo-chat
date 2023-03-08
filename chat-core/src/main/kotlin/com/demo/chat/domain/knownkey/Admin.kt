package com.demo.chat.domain.knownkey

import com.demo.chat.domain.Key

data class Admin<T>(override val id: T, override val empty: Boolean = false) : Key<T>