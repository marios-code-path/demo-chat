package com.demo.chat.domain.knownkey

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ConversationEpoch
import com.demo.chat.domain.FrankingTag
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.core.IKeyService
import java.util.function.Supplier

/**
 * This class mints one root key for every [ChatDomain] through the key
 * service. The key service still takes a class until T3b of `CHAT-avduuqwp`,
 * so each domain names the class that it mints with. T2 replaces this class
 * with `RootKeyLoader`.
 */
class RootKeysSupplier<T>(
    private val keyService: IKeyService<T>,
) : Supplier<Map<ChatDomain, Key<T>>> {

    override fun get(): Map<ChatDomain, Key<T>> =
        ChatDomain.entries.associateWith { domain -> keyService.key(mintClass(domain)).block()!! }

    private fun mintClass(domain: ChatDomain): Class<*> = when (domain) {
        ChatDomain.USER -> User::class.java
        ChatDomain.MESSAGE -> Message::class.java
        ChatDomain.MESSAGE_TOPIC -> MessageTopic::class.java
        ChatDomain.TOPIC_MEMBERSHIP -> TopicMembership::class.java
        ChatDomain.AUTH_METADATA -> AuthMetadata::class.java
        ChatDomain.KEY_VALUE_PAIR -> KeyValuePair::class.java
        ChatDomain.CONVERSATION_EPOCH -> ConversationEpoch::class.java
        ChatDomain.FRANKING_TAG -> FrankingTag::class.java
    }
}
