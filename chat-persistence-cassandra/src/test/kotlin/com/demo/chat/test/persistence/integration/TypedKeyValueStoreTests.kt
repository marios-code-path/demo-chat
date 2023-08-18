package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.persistence.cassandra.impl.KeyValuePersistenceCassandra
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.test.TestLongKeyService
import com.demo.chat.test.persistence.KeyValueStoreTestBase
import com.demo.chat.test.repository.RepositoryTestConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.Resource
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.CassandraContainer
import reactor.core.publisher.Flux
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.random.Random

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = ["app.service.core.kv", "app.key.type=long"])
class TypedKeyValueStoreTests : KeyValueStoreTestBase<Long, Any> {

    @Autowired
    constructor(
        repo: KeyValuePairRepository<Long>,
        mapper: ObjectMapper,
        template: ReactiveCassandraTemplate
    ) : super(
        Supplier { KeyValuePair.create(Key.funKey(1L), "TEST") },
        Supplier { String::class.java },
        KeyValuePersistenceCassandra(
            KeyServiceCassandra(template, TestLongKeyService()), repo, mapper
        ),
        { k -> k.key })

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Autowired
    private lateinit var context: ConfigurableApplicationContext

    @Autowired
    lateinit var container: CassandraContainer<Nothing>

    @Autowired
    @Value("classpath:truncate-\${app.key.type:uuid}.cql")
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