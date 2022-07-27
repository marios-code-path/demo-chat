package com.demo.chat.config

import com.demo.chat.service.MembershipPersistence
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.TopicPersistence
import com.demo.chat.service.UserPersistence
import com.demo.chat.service.security.AuthMetaPersistence

interface PersistenceServiceBeans<T, V> {
    fun userPersistence(): UserPersistence<T> //PersistenceStore<T, User<T>>
    fun topicPersistence(): TopicPersistence<T> //PersistenceStore<T, MessageTopic<T>>
    fun messagePersistence(): MessagePersistence<T, V> //PersistenceStore<T, Message<T, V>>
    fun membershipPersistence(): MembershipPersistence<T> //PersistenceStore<T, TopicMembership<T>>
    fun authMetaPersistence(): AuthMetaPersistence<T> //PersistenceStore<T, AuthMetadata<T>>
}