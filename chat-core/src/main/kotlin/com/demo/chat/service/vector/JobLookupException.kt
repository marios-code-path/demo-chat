package com.demo.chat.service.vector

import com.demo.chat.domain.ChatException

/** The kind of a failed job lookup by topic. See `CHAT-avduuqwp`, D3. */
enum class JobLookupFault(val description: String) {
    /** No index entry names the topic. */
    MISSING("missing"),

    /** Two or more index entries name the topic. */
    DUPLICATE("duplicate"),

    /** The index names a job key that the store does not hold. */
    DANGLING("dangling"),

    /** The stored `job.key` differs from its storage key. */
    STORED_KEY_MISMATCH("stored key mismatch"),

    /** `job.topicKey` differs from the asked topic, by id or by root. */
    TOPIC_MISMATCH("topic mismatch"),
}

/**
 * A job lookup failed. Each fault is an error. No fault returns an empty
 * result, so a reader cannot skip a broken topic and select an older job.
 */
class JobLookupException(val fault: JobLookupFault, detail: String) :
    ChatException("The job lookup failed with a ${fault.description} fault. $detail")
