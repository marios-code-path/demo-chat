package com.demo.chat.config

import com.demo.chat.domain.*
import com.demo.chat.service.IndexService

interface IndexServiceBeans<T, V, Q> {
    fun userIndex(): IndexService<T, User<T>, Q>
    fun messageIndex(): IndexService<T, Message<T, V>, Q>
    fun topicIndex(): IndexService<T, MessageTopic<T>, Q>
    fun membershipIndex(): IndexService<T, TopicMembership<T>, Q>
    fun authMetadataIndex(): IndexService<T, AuthMetadata<T>, Q>
}