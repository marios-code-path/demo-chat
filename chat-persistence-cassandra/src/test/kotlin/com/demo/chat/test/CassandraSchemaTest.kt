package com.demo.chat.test

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.Resource
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.testcontainers.containers.CassandraContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class CassandraSchemaTest {
    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Autowired
    lateinit var container: CassandraContainer<Nothing>

    open lateinit var cqlFile: Resource

    val log = LoggerFactory.getLogger("TEST")

    fun execStatement(statement: Publisher<String>): Flux<Boolean> {
        return template.reactiveCqlOperations
                .execute(statement)
                .doOnNext { bool ->
                    log.info("Completed[$bool]: $statement")
                }
                .doOnError {
                    log.info("ErrCompleted: $statement")
                    log.info("THROWN : ${it.message}")
                }
    }

    @BeforeEach
    fun cqlSetup() {
        val fileData = sqlFile(cqlFile.file)
        val cqlCreate = fileData.filter {
            it.contains("CREATE")
        }

        val cqlDrops = fileData.filter {
            it.contains("DROP")
        }

        execStatement(Flux.fromArray(cqlDrops.toTypedArray()))
                .blockLast()

        execStatement(Flux.fromArray(cqlCreate.toTypedArray()))
                .blockLast()
    }

    fun sqlFile(file: File) =
            String(Files.readAllBytes(file.toPath()))
                    .split(";")
                    .map(String::trim)
                    .filter {
                        it.isNotEmpty()
                    }
}