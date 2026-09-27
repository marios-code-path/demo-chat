package com.demo.chat.domain

import java.time.Instant

/**
 * One progress message of a rebuild. The writer encodes this as a versioned
 * JSON string, because every current deployment binds message data to String.
 */
data class JobRecord<T>(
    override val key: Key<T>,
    val jobKey: Key<T>,
    /** The topic of the job. A record message goes to this destination. See `CHAT-avduuqwp`, D2. */
    val topicKey: Key<T>,
    val workerKey: Key<T>,
    val at: Instant,
    val message: String,
    val errorKey: Key<T>? = null,
    val attempted: Long? = null,
    val indexed: Long? = null,
    val skipped: Long? = null,
    val failed: Long? = null,
) : KeyBearer<T>
