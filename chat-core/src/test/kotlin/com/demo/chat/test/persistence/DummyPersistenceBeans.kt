package com.demo.chat.test.persistence

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.security.AuthMetaPersistence

class DummyPersistenceBeans<T, V> : PersistenceServiceBeans<T, V> {
    override fun userPersistence(): UserPersistence<T> = DummyPersistenceStore<T, User<T>>() as UserPersistence<T>
    override fun topicPersistence(): TopicPersistence<T> = DummyPersistenceStore<T, MessageTopic<T>>() as TopicPersistence<T>
    override fun messagePersistence(): MessagePersistence<T, V> = DummyPersistenceStore<T, V>() as MessagePersistence<T, V>
    override fun membershipPersistence(): MembershipPersistence<T> = DummyPersistenceStore<T, TopicMembership<T>>() as MembershipPersistence<T>
    override fun authMetaPersistence(): AuthMetaPersistence<T> = DummyPersistenceStore<T, AuthMetadata<T>>() as AuthMetaPersistence<T>
}