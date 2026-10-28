package com.demo.chat.shell.portal

/**
 * The argument rules of a register write.
 *
 * The rules match `node-9/bin/register` and `node-9/bin/lib.sh`, so the portal
 * accepts and refuses the same values as the shell path. A refused value
 * raises `INVALID_INPUT`. See CHAT-secgyzgg.
 */
object PortalWriteRules {
    /** The largest key, in characters. */
    const val KEY_LIMIT: Int = 64

    /** The largest entry text, in UTF-8 bytes. */
    const val TEXT_LIMIT: Int = 3072

    /** A short name is lowercase letters, digits, and dashes. */
    private val SHORT = Regex("^[a-z0-9][a-z0-9-]*$")

    /** A request id is letters, digits, dot, dash, colon, and underscore. */
    private val REQUEST_ID = Regex("^[A-Za-z0-9._:-]+$")

    /** The archive key. It names no entry. */
    private const val ARCHIVE_KEY = "-"

    /**
     * Answer a key that can stand in a pointer line.
     *
     * A key holds no `|`, no backslash, and no control character. A key holds
     * no space at its start or its end, and at most [KEY_LIMIT] characters.
     * The key `-` is reserved for archive pointers.
     */
    fun requireKey(key: String?): String {
        if (key.isNullOrEmpty()) refuse("the key is empty")
        if (key.length > KEY_LIMIT) refuse("the key passes $KEY_LIMIT characters")
        if (key.contains('|')) refuse("the key holds the | delimiter")
        if (key.contains('\\')) refuse("the key holds a backslash")
        if (key.any { it.isISOControl() }) refuse("the key holds a control character")
        if (key.first().isWhitespace() || key.last().isWhitespace()) {
            refuse("the key holds a space at its start or its end")
        }
        if (key == ARCHIVE_KEY) refuse("the key $ARCHIVE_KEY is reserved for archive pointers")
        return key
    }

    /** Answer a short name that matches [SHORT]. */
    fun requireShort(short: String?): String {
        if (short == null || !SHORT.matches(short)) {
            refuse("the short name is not lowercase letters, digits, or dashes")
        }
        return short
    }

    /** Answer a title that can stand in a pointer line. */
    fun requireTitle(title: String?): String {
        if (title.isNullOrEmpty()) refuse("the title is empty")
        if (title.contains('|')) refuse("the title holds the | delimiter")
        if (title.any { it.isISOControl() }) refuse("the title holds a control character")
        return title
    }

    /**
     * Answer a request id, or null for an absent one.
     *
     * The shell reads `_` as "new entry on every call", so a retry could post a
     * second entry. The portal refuses it and generates its own id instead.
     */
    fun requireRequestId(requestId: String?): String? {
        if (requestId == null) return null
        if (!REQUEST_ID.matches(requestId)) {
            refuse("the request id is not letters, digits, dot, dash, colon, or underscore")
        }
        if (requestId == "_") refuse("the request id _ asks for a new entry")
        return requestId
    }

    /** Answer a nonblank text of at most [TEXT_LIMIT] UTF-8 bytes. */
    fun requireText(text: String?): String {
        if (text.isNullOrBlank()) refuse("the entry is empty or whitespace only")
        val bytes = text.toByteArray(Charsets.UTF_8).size
        if (bytes > TEXT_LIMIT) refuse("the entry passes $TEXT_LIMIT bytes")
        return text
    }

    private fun refuse(reason: String): Nothing =
        throw PortalProtocolException(PortalErrorCode.INVALID_INPUT, reason)
}
