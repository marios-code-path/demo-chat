package com.demo.chat.test

import com.demo.chat.service.core.IKeyGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.Resource
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.testcontainers.containers.CassandraContainer
import reactor.core.publisher.Flux
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class CassandraSchemaTest<T>(val keyGenerator: IKeyGenerator<T>) {
    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Autowired
    private lateinit var context: ConfigurableApplicationContext

    @Autowired
    lateinit var container: CassandraContainer<Nothing>

    @Autowired
    @Value("classpath:truncate-\${app.service.core.key:uuid}.cql")
    open lateinit var cqlFile: Resource

    val log = LoggerFactory.getLogger("TEST")

    private val atom = AtomicLong(abs(Random.nextLong()))

    fun execStatement(statements: Flux<String>): Flux<Boolean> {
        return template
            .reactiveCqlOperations
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
            it.contains("Truncate")
        }

        execStatement(Flux.fromArray(cqlDrop.toTypedArray()))
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