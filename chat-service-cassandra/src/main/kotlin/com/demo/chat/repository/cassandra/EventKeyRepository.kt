package com.demo.chat.repository.cassandra

import com.demo.chat.domain.CassandraKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import java.util.*

interface EventKeyRepository : ReactiveCassandraRepository<CassandraKey, UUID>
