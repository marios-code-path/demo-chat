package com.demo.chat.service.security

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key

/**
 * A room exists, and its ownership row was not written.
 *
 * **The message names the room key**, so an operator can write the missing row
 * by hand. `Key.toString` answers the id.
 *
 * The room keeps its store row, its index row and its open topic. No step of
 * the `addRoom` chain compensates another, so the residual is an ownerless
 * room. See `CHAT-zhjltbky`.
 *
 * **The key type is open**, because a subclass of `Throwable` cannot carry a
 * type parameter. Kotlin refuses it at the declaration.
 */
class RoomOwnerGrantException(val roomKey: Key<*>, cause: Throwable) :
    ChatException("The room owner grant failed for room $roomKey. The room exists and it has no owner.", cause)
