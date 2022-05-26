package com.demo.chat.service

/**
 * Tracks anything that listens to a message channel for the purpose of operations
 */
interface SubscriptionDirectory<T> {
    fun getSubscribersFor(id: T): Collection<T>
    fun subscribeTo(left: T, right: T): Unit
    fun unsubscribe(left: T, right: T): Unit
}