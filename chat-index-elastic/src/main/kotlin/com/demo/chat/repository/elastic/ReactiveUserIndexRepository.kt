package com.demo.chat.repository.elastic

import com.demo.chat.domain.User
import com.demo.chat.domain.elastic.Foo
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository

interface ReactiveUserIndexRepository<T> : ReactiveElasticsearchRepository<User<T>, T>

interface FooRepository: ReactiveElasticsearchRepository<Foo, String>