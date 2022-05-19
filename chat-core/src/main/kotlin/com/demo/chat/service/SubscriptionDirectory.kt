package com.demo.chat.service

interface
SubscriptionDirectory<T> {
    fun getSubscribersFor(id: T): Collection<T>
    fun subscribeTo(left: T, right: T): Unit
    fun unsubscribe(left:T, right:T): Unit
}