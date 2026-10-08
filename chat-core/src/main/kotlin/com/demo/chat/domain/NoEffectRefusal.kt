package com.demo.chat.domain

/**
 * A refusal that happens before any backend effect. Decision 12 of the spec.
 * Only these errors are definitive. Any other error after an attempt starts
 * can follow an effect, so the runtime records it as uncertain.
 */
interface NoEffectRefusal

/** A general refusal before any effect, for a handler that has no narrower type. */
class DefinitiveRefusalException(message: String) : ChatException(message), NoEffectRefusal
