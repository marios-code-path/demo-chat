package com.demo.chat.domain

import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
data class IndexSearchRequest(val first: String, val second: String, val third: Int)