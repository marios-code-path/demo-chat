package com.demo.chat.config

import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex

interface IndexServiceBeans<T, V, Q> {
    fun userIndex(): UserIndexService<T, Q>//IndexService<T, User<T>, Q>
    fun messageIndex(): MessageIndexService<T, V, Q> //IndexService<T, Message<T, V>, Q>
    fun topicIndex(): TopicIndexService<T, Q> //IndexService<T, MessageTopic<T>, Q>
    fun membershipIndex(): MembershipIndexService<T, Q> //IndexService<T, TopicMembership<T>, Q>
    fun authMetadataIndex(): AuthMetaIndex<T, Q> //IndexService<T, AuthMetadata<T>, Q>
}