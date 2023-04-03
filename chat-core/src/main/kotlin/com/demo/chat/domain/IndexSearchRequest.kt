package com.demo.chat.domain

import org.springframework.boot.context.properties.bind.ConstructorBinding

data class IndexSearchRequest @ConstructorBinding constructor(val first: String, val second: String, val config: Int) {
    fun toMap(): Map<String, Any> = mapOf(first to second, "third" to config)
}