package com.demo.chat.test.persistence

import com.demo.chat.service.persistence.KeyPersistenceCassandra
import com.demo.chat.test.KeyServiceBaseTest
import com.demo.chat.test.TestConfiguration
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [TestConfiguration::class])
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-keys.cql")
class KeyPersistenceCassandraTests : KeyServiceBaseTest() {
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    @Autowired
    private lateinit var template: ReactiveCassandraTemplate

    @BeforeEach
    fun setUp() {
        logger.info("Setup persistence cassandra")
        this.svc = KeyPersistenceCassandra(template)
    }

    private val handle = "darkbit"
}