package com.demo.chat.persistence.cassandra.repository

import com.demo.chat.persistence.cassandra.domain.CSKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository

interface EventKeyRepository<T> : ReactiveCassandraRepository<CSKey<T>, T>