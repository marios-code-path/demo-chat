package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.CassandraKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository

interface EventKeyRepository<T> : ReactiveCassandraRepository<CassandraKey<T>, T>
