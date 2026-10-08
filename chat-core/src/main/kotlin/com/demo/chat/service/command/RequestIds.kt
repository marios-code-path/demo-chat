package com.demo.chat.service.command

import com.demo.chat.domain.command.InvalidRequestIdException

/** A request ID has 1 to 128 visible ASCII characters, from '!' to '~'. */
object RequestIds {
    const val MAX_LENGTH = 128

    fun requireValid(requestId: String?): String {
        if (requestId.isNullOrEmpty()) throw InvalidRequestIdException("It is empty.")
        if (requestId.length > MAX_LENGTH) {
            throw InvalidRequestIdException("It has ${requestId.length} characters. The limit is $MAX_LENGTH.")
        }
        if (requestId.any { it < '!' || it > '~' }) {
            throw InvalidRequestIdException("It holds a character outside visible ASCII.")
        }
        return requestId
    }
}
