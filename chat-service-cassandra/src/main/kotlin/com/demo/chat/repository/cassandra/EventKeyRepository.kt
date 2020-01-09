package com.demo.chat.repository.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.cassandra.CassandraKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import java.util.*

interface EventKeyRepository<K> : ReactiveCassandraRepository<CassandraKey<K>, K>
