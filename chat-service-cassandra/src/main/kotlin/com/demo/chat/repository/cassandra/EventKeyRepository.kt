package com.demo.chat.repository.cassandra

import com.demo.chat.domain.CassandraEventKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import java.util.*

interface EventKeyRepository : ReactiveCassandraRepository<CassandraEventKey, UUID>
