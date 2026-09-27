package com.demo.chat.service.core

/**
 * The start check of a store shape. See `CHAT-avduuqwp`, T7.
 *
 * A store written before the key root work lacks elements that this release
 * reads. This release has no migration. So [check] fails with a message that
 * names each missing element and the recreation, before the root keys load.
 */
fun interface StoreShapeCheck {
    /** Returns when the store has the complete shape. Throws `ChatException` otherwise. */
    fun check()
}
