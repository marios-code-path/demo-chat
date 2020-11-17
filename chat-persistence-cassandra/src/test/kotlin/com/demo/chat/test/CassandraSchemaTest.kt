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
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class CassandraSchemaTest {
    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Autowired
    lateinit var container: CassandraContainer<Nothing>

    open lateinit var cqlFile: Resource

    val log = LoggerFactory.getLogger("TEST")

    fun execStatement(statements: Flux<String>): Flux<Boolean> {

        return template.reactiveCqlOperations
                .execute(statements)
                .doOnError {
                    statements
                            .doOnNext { println("exec: $it") }
                            .blockLast()
                    log.info("ErrCompleted: $statements.")
                    log.info("THROWN : ${it.message}")
                }
    }

    @BeforeEach
    fun cqlSetup() {
        val fileData = sqlFile(cqlFile.file)
        val cqlDrop = fileData.filter {
            it.contains("DROP")
        }
        val cqlCreate = fileData.filter {
            it.contains("CREATE")
        }
        //TODO: KLUDGE to eliminate PT2S
        Thread.sleep(2000)
        // Thread Rip Me
        execStatement(Flux.fromArray(cqlDrop.toTypedArray()))
                .blockLast(Duration.ofSeconds(5))

        execStatement(Flux.fromArray(cqlCreate.toTypedArray()))
                .blockLast(Duration.ofSeconds(5))
    }

    fun sqlFile(file: File) =
            String(Files.readAllBytes(file.toPath()))
                    .split(";")
                    .map(String::trim)
                    .filter {
                        it.isNotEmpty()
                    }
}