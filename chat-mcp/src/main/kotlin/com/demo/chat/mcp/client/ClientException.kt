package com.demo.chat.mcp.client

/**
 * The class of a backend failure.
 *
 * A caller must tell a topic that the backend does not serve from a backend
 * that is broken. That distinction decides whether a list omits one entry or
 * fails whole.
 */
enum class FailureReason {
    /**
     * The backend serves no such object, or it refuses the caller.
     *
     * One reason covers both a 403 and a 404 on purpose. A caller must not be
     * able to tell a hidden object from an absent one.
     */
    NOT_AVAILABLE,

    /** The backend refuses the credential. */
    AUTHENTICATION,

    /** The backend answered a status that no other reason claims. */
    BACKEND,

    /** The connection failed, or the call passed its deadline. */
    TRANSPORT,

    /** A response broke a size limit, or the adapter holds its request limit. */
    LIMIT,

    /** A response broke the envelope contract. */
    PROTOCOL,
}

/**
 * A backend call that the adapter refuses, or cannot complete.
 *
 * The message names the rule and the failing field. It carries no credential
 * and no message text.
 *
 * The reason is required. A default would let a new throw site name no class,
 * and the wrong class is silent at the list boundary.
 *
 * The status is the backend HTTP status, when a response carried one. It is
 * null for a connection that never opened. The diagnostic line reports it, so
 * an operator reads the status without reading the message.
 */
class ClientException(
    message: String,
    val reason: FailureReason,
    val status: Int? = null,
) : RuntimeException(message)
