package com.demo.chat.test.index

import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
open class IndexTestBase<T, E, Q>(val valueSupply: Supplier<E>,
                                  val keyService: IKeyService<T>,
                                  val querySupply: Supplier<Q>,
                                  val index: IndexService<T, E, Q>) {

    @Test
    fun `should save one`() {
        StepVerifier
                .create(index.add(valueSupply.get()))
                .verifyComplete()
    }

    @Test
    fun `should remove one`() {
        StepVerifier
                .create(
                        keyService
                                .key(String::class.java)
                                .flatMap(index::rem))
    }

    @Test
    fun `should findBy simple`() {
        StepVerifier
                .create(index.findBy(querySupply.get()))
    }
}