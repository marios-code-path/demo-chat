package com.demo.chat.repository.elastic

import com.demo.chat.domain.elastic.ChatUser
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository

interface ReactiveUserIndexRepository<T> : ReactiveElasticsearchRepository<ChatUser<T>, T>

