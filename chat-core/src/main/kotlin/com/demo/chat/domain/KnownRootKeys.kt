package com.demo.chat.domain

import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon

class KnownRootKeys {
    companion object {
        val knownRootKeys: Set<Class<*>> = setOf(
            User::class.java,
            Message::class.java,
            MessageTopic::class.java,
            TopicMembership::class.java,
            AuthMetadata::class.java,
            KeyDataPair::class.java,
            Anon::class.java,
            Admin::class.java
        )
    }
}