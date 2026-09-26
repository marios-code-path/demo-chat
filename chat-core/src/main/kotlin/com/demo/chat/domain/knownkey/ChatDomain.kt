package com.demo.chat.domain.knownkey

/**
 * This enum lists every domain that owns a root key. See `CHAT-avduuqwp`.
 *
 * **The list is closed.** A type with no entry has no root, and a mint for it
 * is refused. [parse] is the only way that text becomes a domain.
 */
enum class ChatDomain(val wireName: String) {
    USER("User"),
    MESSAGE("Message"),
    MESSAGE_TOPIC("MessageTopic"),
    TOPIC_MEMBERSHIP("TopicMembership"),
    AUTH_METADATA("AuthMetadata"),
    KEY_VALUE_PAIR("KeyValuePair"),
    CONVERSATION_EPOCH("ConversationEpoch"),
    FRANKING_TAG("FrankingTag");

    companion object {
        fun parse(name: String): ChatDomain? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * This enum lists the user identities that `RootKeys` holds beside the domain
 * roots. Each identity names one user, so it is an object of the `User`
 * domain and not a domain.
 */
enum class ChatIdentity(val wireName: String) {
    ADMIN("Admin"),
    ANON("Anon");

    companion object {
        fun parse(name: String): ChatIdentity? = entries.firstOrNull { it.wireName == name }
    }
}
