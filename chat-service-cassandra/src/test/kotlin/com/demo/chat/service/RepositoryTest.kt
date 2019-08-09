package com.demo.chat.service

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@Import(PersonRepository::class)
class RepositoryTest {

    @Autowired
    private lateinit var personRepo: PersonRepository

    @Test
    fun `repository should be available`() {
        Assertions
                .assertThat(personRepo.findByFirstName("foo"))
                .hasAtLeastOneElementOfType(Person::class.java)

    }
}

interface PersonRepository : PersonRepositoryCustom

interface PersonRepositoryCustom {
    fun findByFirstName(firstName: String): Collection<Person>
}

class PersonRepositoryImpl : PersonRepositoryCustom {
    override fun findByFirstName(firstName: String): Collection<Person> = listOf(Person("foo"), Person("bar"))

}

data class Person(val firstName: String)