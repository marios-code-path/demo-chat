package com.demo.chat.domain

import com.demo.chat.domain.knownkey.ChatDomain

/**
 * A pointer target that is missing, or that the caller cannot read. Both give
 * the same text, so an id leaks nothing. See `CHAT-erkoxybm`.
 */
class NoSuchPointerTargetException(val domain: ChatDomain) :
    ChatException(if (domain == ChatDomain.MESSAGE) "no such message" else "no such topic"), NoEffectRefusal

/** A pointer to a pointer. No stored chain can exist. See `CHAT-erkoxybm`. */
class PointerToPointerException : ChatException("a pointer cannot target a pointer"), NoEffectRefusal
