package com.demo.chat.domain

class KnownRootKeys {
    companion object {
        val knownRootKeys: Set<Class<*>> = setOf(
            User::class.java,
            Message::class.java,
            MessageTopic::class.java,
            TopicMembership::class.java,
            AuthMetadata::class.java,
            KeyValuePair::class.java,
        )
    }
}