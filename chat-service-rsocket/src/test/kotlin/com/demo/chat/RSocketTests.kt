package com.demo.chat

import io.rsocket.AbstractRSocket
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension


@ExtendWith(SpringExtension::class)
class RSocketTests {


    private lateinit var testRsocket : AbstractRSocket

    @BeforeAll
    fun setUp() {

    }

    @Test
    fun `should connect to test rsocket`() {

    }
}