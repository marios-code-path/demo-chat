package com.demo.chat.deploy.config.core

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.*

interface IndexServiceConfiguration<T, V, Q> {
    fun userIndex(): IndexService<T, User<T>, Q>
    fun messageIndex(): IndexService<T, Message<T, V>, Q>
    fun topicIndex(): IndexService<T, MessageTopic<T>, Q>
    fun membershipIndex(): IndexService<T, TopicMembership<T>, Q>
}