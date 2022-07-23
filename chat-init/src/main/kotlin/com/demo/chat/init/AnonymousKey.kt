package com.demo.chat.init

import com.demo.chat.domain.Key

data class AnonymousKey<T>(override val id: T) : Key<T>