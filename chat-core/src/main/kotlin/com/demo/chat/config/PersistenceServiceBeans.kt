package com.demo.chat.config

import com.demo.chat.service.MembershipPersistence
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.TopicPersistence
import com.demo.chat.service.UserPersistence
import com.demo.chat.service.security.AuthMetaPersistence

interface PersistenceServiceBeans<T, V> {
    fun user(): UserPersistence<T> //PersistenceStore<T, User<T>>
    fun topic(): TopicPersistence<T> //PersistenceStore<T, MessageTopic<T>>
    fun message(): MessagePersistence<T, V> //PersistenceStore<T, Message<T, V>>
    fun membership(): MembershipPersistence<T> //PersistenceStore<T, TopicMembership<T>>
    fun authMetadata(): AuthMetaPersistence<T> //PersistenceStore<T, AuthMetadata<T>>
}