package com.demo.chat.test.config

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaPersistence
import org.mockito.BDDMockito

class TestPersistenceBeans<T, V> : PersistenceServiceBeans<T, V> {

    private val userPersistence: UserPersistence<T> =
        BDDMockito.mock(UserPersistence::class.java) as UserPersistence<T>

    private val topicPersistence: TopicPersistence<T> =
        BDDMockito.mock(TopicPersistence::class.java) as TopicPersistence<T>

    private val messagePersistence: MessagePersistence<T, V> =
        BDDMockito.mock(MessagePersistence::class.java) as MessagePersistence<T, V>

    private val membershipPersistence: MembershipPersistence<T> =
        BDDMockito.mock(MembershipPersistence::class.java) as MembershipPersistence<T>

    private val authMetaPersistence: AuthMetaPersistence<T> =
        BDDMockito.mock(AuthMetaPersistence::class.java) as AuthMetaPersistence<T>

    private val kvPersistence: KeyValueStore<T, Any> =
        BDDMockito.mock(KeyValueStore::class.java) as KeyValueStore<T, Any>

    override fun userPersistence() = userPersistence

    override fun topicPersistence() = topicPersistence

    override fun messagePersistence() = messagePersistence

    override fun membershipPersistence() = membershipPersistence

    override fun authMetaPersistence() = authMetaPersistence

    override fun keyValuePersistence() = kvPersistence
}