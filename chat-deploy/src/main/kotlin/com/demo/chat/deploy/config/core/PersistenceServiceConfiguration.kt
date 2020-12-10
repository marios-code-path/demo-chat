package com.demo.chat.deploy.config.core

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.*

interface PersistenceServiceConfiguration<T, V> {
    fun user(): PersistenceStore<T, User<T>>
    fun topic(): PersistenceStore<T, MessageTopic<T>>
    fun message(): PersistenceStore<T, Message<T, V>>
    fun membership(): PersistenceStore<T, TopicMembership<T>>
}