package com.demo.chat.domain

/**
 * Device registration for E2EE — identity is (user, device). All crypto is per-device.
 * The server stores only public keys and pre-key bundles. Private keys never leave the device.
 */
interface DeviceRegistration<T> : KeyBearer<T> {
    val userId: Key<T>
    val registrationId: Int
    val identityKeyPub: ByteArray
    val signedPreKey: ByteArray
    val signedPreKeySig: ByteArray
    val signedPreKeyId: Int
    val createdAt: Long

    companion object Factory {
        fun <T> create(
            deviceId: Key<T>,
            userId: Key<T>,
            registrationId: Int,
            identityKeyPub: ByteArray,
            signedPreKey: ByteArray,
            signedPreKeySig: ByteArray,
            signedPreKeyId: Int
        ): DeviceRegistration<T> = object : DeviceRegistration<T> {
            override val key: Key<T> = deviceId
            override val userId: Key<T> = userId
            override val registrationId: Int = registrationId
            override val identityKeyPub: ByteArray = identityKeyPub
            override val signedPreKey: ByteArray = signedPreKey
            override val signedPreKeySig: ByteArray = signedPreKeySig
            override val signedPreKeyId: Int = signedPreKeyId
            override val createdAt: Long = System.currentTimeMillis()

            override fun equals(other: Any?): Boolean =
                other != null && other is DeviceRegistration<*> && other.key == this.key

            override fun hashCode(): Int = key.hashCode()
        }
    }
}

/**
 * One-time pre-key bundle for X3DH key agreement. The server holds a pool of these
 * per device. When a new session is established, one pre-key is consumed.
 */
interface PreKeyBundle<T> : KeyBearer<T> {
    val deviceId: Key<T>
    val preKeyId: Int
    val preKeyPub: ByteArray
    val signedPreKeyId: Int
    val signedPreKeyPub: ByteArray
    val signedPreKeySig: ByteArray
    val identityKeyPub: ByteArray
    val userId: Key<T>

    companion object Factory {
        fun <T> create(
            bundleId: Key<T>,
            deviceId: Key<T>,
            userId: Key<T>,
            preKeyId: Int,
            preKeyPub: ByteArray,
            signedPreKeyId: Int,
            signedPreKeyPub: ByteArray,
            signedPreKeySig: ByteArray,
            identityKeyPub: ByteArray
        ): PreKeyBundle<T> = object : PreKeyBundle<T> {
            override val key: Key<T> = bundleId
            override val deviceId: Key<T> = deviceId
            override val userId: Key<T> = userId
            override val preKeyId: Int = preKeyId
            override val preKeyPub: ByteArray = preKeyPub
            override val signedPreKeyId: Int = signedPreKeyId
            override val signedPreKeyPub: ByteArray = signedPreKeyPub
            override val signedPreKeySig: ByteArray = signedPreKeySig
            override val identityKeyPub: ByteArray = identityKeyPub

            override fun equals(other: Any?): Boolean =
                other != null && other is PreKeyBundle<*> && other.key == this.key

            override fun hashCode(): Int = key.hashCode()
        }
    }
}

/**
 * Encrypted envelope — the per-device ciphertext envelope stored in the device inbox.
 * The server never sees plaintext. It routes by conversationId + seq + deviceId.
 */
interface EncryptedEnvelope<T> : KeyBearer<T> {
    val conversationId: Key<T>
    val senderUserId: Key<T>
    val senderDeviceId: Key<T>
    val recipientDeviceId: Key<T>
    val seq: Long
    val messageKind: MessageKind
    val ciphertext: ByteArray
    val serverTimestamp: Long
    val expiresAt: Long?

    companion object Factory {
        fun <T> create(
            envelopeId: Key<T>,
            conversationId: Key<T>,
            senderUserId: Key<T>,
            senderDeviceId: Key<T>,
            recipientDeviceId: Key<T>,
            seq: Long,
            messageKind: MessageKind,
            ciphertext: ByteArray,
            expiresAt: Long? = null
        ): EncryptedEnvelope<T> = object : EncryptedEnvelope<T> {
            override val key: Key<T> = envelopeId
            override val conversationId: Key<T> = conversationId
            override val senderUserId: Key<T> = senderUserId
            override val senderDeviceId: Key<T> = senderDeviceId
            override val recipientDeviceId: Key<T> = recipientDeviceId
            override val seq: Long = seq
            override val messageKind: MessageKind = messageKind
            override val ciphertext: ByteArray = ciphertext
            override val serverTimestamp: Long = System.currentTimeMillis()
            override val expiresAt: Long? = expiresAt

            override fun equals(other: Any?): Boolean =
                other != null && other is EncryptedEnvelope<*> && other.key == this.key

            override fun hashCode(): Int = key.hashCode()
        }
    }
}

/**
 * Conversation cursor — per-conversation sequence allocator.
 * Uses Postgres row-lock for linearizable ordering (no gaps for committed messages).
 */
interface ConversationCursor<T> {
    val conversationId: Key<T>
    var nextSeq: Long

    companion object Factory {
        fun <T> create(conversationId: Key<T>, nextSeq: Long = 1L): ConversationCursor<T> =
            object : ConversationCursor<T> {
                override val conversationId: Key<T> = conversationId
                override var nextSeq: Long = nextSeq
            }
    }
}

/**
 * Conversation epoch — server-visible membership boundary.
 * The server cannot understand crypto membership, but it can enforce transport membership
 * and prevent obvious history leaks across epoch boundaries.
 */
interface ConversationEpoch<T> : KeyBearer<T> {
    val conversationId: Key<T>
    val epoch: Int
    val startedAt: Long
    val endedAt: Long?

    companion object Factory {
        fun <T> create(
            epochId: Key<T>,
            conversationId: Key<T>,
            epoch: Int
        ): ConversationEpoch<T> = object : ConversationEpoch<T> {
            override val key: Key<T> = epochId
            override val conversationId: Key<T> = conversationId
            override val epoch: Int = epoch
            override val startedAt: Long = System.currentTimeMillis()
            override val endedAt: Long? = null
        }
    }
}

enum class MessageKind {
    PAIRWISE,
    SENDER_KEY,
    MLS
}

/**
 * Franking tag — server-generated proof tag bound to a specific message.
 * Used for abuse reporting without revealing plaintext.
 */
interface FrankingTag<T> : KeyBearer<T> {
    val conversationId: Key<T>
    val seq: Long
    val senderDeviceId: Key<T>
    val messageKind: MessageKind
    val tag: ByteArray
    val frankingKeyId: Int

    companion object Factory {
        fun <T> create(
            tagId: Key<T>,
            conversationId: Key<T>,
            seq: Long,
            senderDeviceId: Key<T>,
            messageKind: MessageKind,
            tag: ByteArray,
            frankingKeyId: Int
        ): FrankingTag<T> = object : FrankingTag<T> {
            override val key: Key<T> = tagId
            override val conversationId: Key<T> = conversationId
            override val seq: Long = seq
            override val senderDeviceId: Key<T> = senderDeviceId
            override val messageKind: MessageKind = messageKind
            override val tag: ByteArray = tag
            override val frankingKeyId: Int = frankingKeyId
        }
    }
}

/**
 * History visibility policy for a conversation.
 * v1: SINCE_JOIN only (no shared history — cut from v1 per Codex Round 2 review).
 */
enum class HistoryVisibility {
    SINCE_JOIN,
    SHARED
}

/**
 * Presence state — ephemeral, never persisted durably.
 * The server tracks online/away/offline via heartbeat TTL keys.
 */
interface Presence<T> {
    val userId: Key<T>
    val deviceId: Key<T>
    val state: PresenceState
    val lastHeartbeat: Long

    companion object Factory {
        fun <T> create(
            userId: Key<T>,
            deviceId: Key<T>,
            state: PresenceState
        ): Presence<T> = object : Presence<T> {
            override val userId: Key<T> = userId
            override val deviceId: Key<T> = deviceId
            override val state: PresenceState = state
            override val lastHeartbeat: Long = System.currentTimeMillis()
        }
    }
}

enum class PresenceState {
    ONLINE,
    AWAY,
    OFFLINE,
    TYPING
}
