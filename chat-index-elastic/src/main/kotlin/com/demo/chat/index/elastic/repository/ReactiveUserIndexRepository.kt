package com.demo.chat.index.elastic.repository

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.index.elastic.domain.ChatUser
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository

interface ReactiveUserIndexRepository<T> : ReactiveElasticsearchRepository<ChatUser<T>, T>

interface ReactiveMessageIndexRepository<T, E> : ReactiveElasticsearchRepository<Message<T, E>, T>

interface ReactiveTopicIndexRepository<T, E> : ReactiveElasticsearchRepository<MessageTopic<T>, T>

interface ReactiveTopicMembershipIndexRepository<T> : ReactiveElasticsearchRepository<TopicMembership<T>, T>