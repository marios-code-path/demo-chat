package com.demo.chat.test.repository.uuid

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.data.repository.reactive.ReactiveSortingRepository
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
abstract class RepositoryTests<T, E>(
    val valueSupply: Supplier<E>,
    val keySupply: Supplier<T>
) {
    lateinit var repo: ReactiveSortingRepository<E, T>

    abstract fun assertElement(element: E): Unit

    @Test
    fun <T> `should save and findById`() {
        val users = Flux.just(
            valueSupply.get(),
            valueSupply.get(),
            valueSupply.get()
        )
            .flatMap {
                repo.save(it)
            }

        val find = repo.findById(keySupply.get())

        val saveAndFind = Flux.from(users)
            .then(find)

        StepVerifier.create(saveAndFind)
            .expectSubscription()
            .assertNext(this::assertElement)
            .verifyComplete()
    }

    @Test
    fun `save all and find all`() {
        val saveAndFind = repo
            .saveAll(Flux.just(valueSupply.get(), valueSupply.get()))
            .thenMany(repo.findAll())

        StepVerifier.create(saveAndFind)
            .assertNext(this::assertElement)
            .assertNext(this::assertElement)
            .verifyComplete()
    }

    @Test
    fun `should save and findByID`() {
        val saveAndFindById = repo
            .save(valueSupply.get())
            .thenMany(repo.findById(keySupply.get()))

        StepVerifier.create(saveAndFindById)
            .expectSubscription()
            .assertNext(this::assertElement)
            .verifyComplete()
    }
}