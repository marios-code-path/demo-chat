package com.demo.chat.test.controller.webflux.config

import com.demo.chat.domain.knownkey.ChatDomain

/** The domain behind each REST path of the slice tests. See `CHAT-avduuqwp`. */
object WebFluxTestDomains {
    fun of(path: String): ChatDomain = when (path) {
        "user" -> ChatDomain.USER
        "message" -> ChatDomain.MESSAGE
        "topic" -> ChatDomain.MESSAGE_TOPIC
        "membership" -> ChatDomain.TOPIC_MEMBERSHIP
        "kv" -> ChatDomain.KEY_VALUE_PAIR
        "auth" -> ChatDomain.AUTH_METADATA
        else -> throw IllegalArgumentException("No domain for the path '$path'.")
    }
}
