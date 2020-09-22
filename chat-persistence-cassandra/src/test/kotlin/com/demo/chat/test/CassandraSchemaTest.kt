package com.demo.chat.test

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Flux
import java.nio.file.Files

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class CassandraSchemaTest {
    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    open lateinit var cqlFile: Resource

    @BeforeAll
    fun cqlSetup() {
        val cqlCreate = String(Files.readAllBytes(cqlFile.file.toPath())).split(";").filter {
            it.trim().length > 1
        }

        template.reactiveCqlOperations
                .execute(Flux.fromArray(cqlCreate.toTypedArray()))
                .blockLast()
    }
}