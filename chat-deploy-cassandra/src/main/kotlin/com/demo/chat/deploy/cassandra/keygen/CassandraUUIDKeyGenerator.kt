package com.demo.chat.deploy.cassandra.keygen

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.service.IKeyGenerator
import java.util.*

class CassandraUUIDKeyGenerator : IKeyGenerator<UUID> {
    override fun nextKey(): UUID = Uuids.timeBased()
}