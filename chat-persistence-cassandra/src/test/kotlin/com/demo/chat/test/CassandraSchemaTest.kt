package com.demo.chat.test

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.testcontainers.containers.CassandraContainer
import reactor.core.publisher.Flux
import java.io.File
import java.nio.file.Files
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class CassandraSchemaTest {
    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Autowired
    lateinit var container: CassandraContainer<Nothing>

    open lateinit var cqlFile: Resource

    @BeforeAll
    fun cqlSetup() {
        val cqlKeysp = sqlFile(ClassPathResource("classpath:keyspace.cql").file)
        val cqlCreate = sqlFile(cqlFile.file)

        template.reactiveCqlOperations
                .execute(Flux.fromArray(cqlKeysp.toTypedArray()))
                .blockLast()

        template.reactiveCqlOperations
                .execute(Flux.fromArray(cqlCreate.toTypedArray()))
                .blockLast()
    }

    fun sqlFile(file: File)  =
        String(Files.readAllBytes(file.toPath()))
                .split(";").filter {
                    it.trim().length > 1
                }

    @AfterAll
    fun shutdownStuff() {
        container.stop()
    }
}