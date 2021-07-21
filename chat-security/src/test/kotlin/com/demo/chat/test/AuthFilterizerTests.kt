package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.secure.AuthFilterizer
import com.demo.chat.service.AuthMetadata
import com.demo.chat.service.StringRoleAuthorizationMetadata
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.function.Supplier
import kotlin.random.Random

class AuthFilterizerTests {

    val keyGen = Supplier { Key.funKey(Random.nextLong()) }

    @Test
    fun `filterizer create()`() {
        Assertions
            .assertThat(AuthFilterizer<Long, String>())
            .isNotNull
    }

    @Test
    fun `filterizer element is removed during pass`() {
        val filterizer = AuthFilterizer<Long, String>()

        val anonId = keyGen.get()
        val idSeq = sequenceOf(anonId)

        val filterData: Flux<AuthMetadata<Long, String>> = Flux.just(
            StringRoleAuthorizationMetadata(keyGen.get(), keyGen.get(), keyGen.get(), "ALL", 0L)
        )

        StepVerifier.create(filterizer.filterize(filterData, idSeq))
            .verifyComplete()
    }
}