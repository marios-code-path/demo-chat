package com.demo.chat.persistence.cassandra.domain.keygen

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.service.core.IKeyGenerator
import java.util.*

class CassandraUUIDKeyGenerator : IKeyGenerator<UUID> {
    override fun nextId(): UUID = Uuids.timeBased()
}