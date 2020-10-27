package com.demo.chat.test.indexrepo

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class ElasticSchemaTest {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @BeforeEach
    fun setUpElastic() {

    }
}