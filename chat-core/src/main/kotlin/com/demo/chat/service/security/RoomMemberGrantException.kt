package com.demo.chat.service.security

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key

/**
 * A membership changed, and its `SEND` row was not written.
 *
 * **The message names the member key and the room key**, so an operator can
 * write the missing row by hand. The membership change stays, because no step
 * of the join or leave chain compensates another. See `CHAT-mfveaecc`.
 *
 * **The key type is open**, because a subclass of `Throwable` cannot carry a
 * type parameter.
 */
class RoomMemberGrantException(val memberKey: Key<*>, val roomKey: Key<*>, cause: Throwable) :
    ChatException("The room member grant failed for member $memberKey in room $roomKey.", cause)
