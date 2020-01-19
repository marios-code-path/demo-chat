package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.CSKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository

interface EventKeyRepository<T> : ReactiveCassandraRepository<CSKey<T>, T>
