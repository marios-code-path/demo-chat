package com.demo.chat.test.domain

import com.demo.chat.domain.SnowflakeGenerator
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration

class SnowflakeGeneratorTests {

    @Test
    fun `should generate a few sequences`() {
        val generator = SnowflakeGenerator()

        val publisher = Flux.create<Long> { sink ->
            sink.next(generator.nextId())
            sink.complete()
        }
            .repeat(10)
            .delayElements(Duration.ofMillis(10))


        StepVerifier
            .create(publisher)
            .thenConsumeWhile { s ->
                s != null
            }
            .verifyComplete()

    }
}