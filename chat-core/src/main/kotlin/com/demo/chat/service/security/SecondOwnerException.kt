package com.demo.chat.service.security

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key

/**
 * A target already has an owner, and a second owner row was refused.
 *
 * An owner row is a live `*` row whose principal is an entity. One target
 * holds one owner. A close row names a domain root, so it is not an owner row
 * and this refusal does not reach it. The owner decided this on 2026-10-04.
 * See `CHAT-esengqpv`.
 *
 * **The message names the target key**, so an operator can find the existing
 * owner row. `Key.toString` answers the id.
 *
 * **The key type is open**, because a subclass of `Throwable` cannot carry a
 * type parameter. Kotlin refuses it at the declaration.
 */
class SecondOwnerException(val target: Key<*>) :
    ChatException("Target $target already has an owner. A second owner row is refused.")
