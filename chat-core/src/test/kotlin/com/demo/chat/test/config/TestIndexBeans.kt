package com.demo.chat.test.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex
import org.mockito.BDDMockito

class TestIndexBeans<T, V, Q> : IndexServiceBeans<T, V, Q> {
    val userIndex: UserIndexService<T, Q> = BDDMockito.mock(UserIndexService::class.java) as UserIndexService<T, Q>
    val messageIndex: MessageIndexService<T, V, Q>  = BDDMockito.mock(MessageIndexService::class.java) as MessageIndexService<T, V, Q>
    val topicIndex: TopicIndexService<T, Q> = BDDMockito.mock(TopicIndexService::class.java) as TopicIndexService<T, Q>
    val membershipIndex: MembershipIndexService<T, Q> = BDDMockito.mock(MembershipIndexService::class.java) as MembershipIndexService<T, Q>
    val authMetadataIndex: AuthMetaIndex<T, Q> = BDDMockito.mock(AuthMetaIndex::class.java) as AuthMetaIndex<T, Q>

    override fun userIndex(): UserIndexService<T, Q> = userIndex

    override fun messageIndex(): MessageIndexService<T, V, Q> = messageIndex

    override fun topicIndex(): TopicIndexService<T, Q> = topicIndex

    override fun membershipIndex(): MembershipIndexService<T, Q> = membershipIndex

    override fun authMetadataIndex(): AuthMetaIndex<T, Q> = authMetadataIndex
}