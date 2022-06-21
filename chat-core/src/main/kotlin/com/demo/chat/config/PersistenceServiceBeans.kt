package com.demo.chat.config

import com.demo.chat.domain.*
import com.demo.chat.service.PersistenceStore

interface PersistenceServiceBeans<T, V> {
    fun user(): PersistenceStore<T, User<T>>
    fun topic(): PersistenceStore<T, MessageTopic<T>>
    fun message(): PersistenceStore<T, Message<T, V>>
    fun membership(): PersistenceStore<T, TopicMembership<T>>
    fun authMetadata(): PersistenceStore<T, AuthMetadata<T>>
}