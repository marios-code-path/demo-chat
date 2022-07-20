package com.demo.chat.test.repository

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.domain.cassandra.TopicMembershipByMember
import com.demo.chat.domain.cassandra.TopicMembershipByMemberOf
import com.demo.chat.repository.cassandra.TopicMembershipByMemberOfRepository
import com.demo.chat.repository.cassandra.TopicMembershipByMemberRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestUUIDKeyGenerator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class])
class MembershipIndexRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    @Autowired
    lateinit var byMemberRepo: TopicMembershipByMemberRepository<UUID>

    @Autowired
    lateinit var byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>

    @Test
    fun shouldContextLoad() {
        assertAll("Reactive Template Exists",
            { org.junit.jupiter.api.Assertions.assertNotNull(template) })
    }

    @Test
    fun `membershipOf should not return all`() {
        val membership = TopicMembershipByMemberOf(Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased())

        val membershipSave = byMemberOfRepo
            .save(membership)
            .thenMany(byMemberOfRepo.findAll())

        StepVerifier
            .create(membershipSave)
            .assertNext {

            }
            .verifyComplete()
    }

    //@Test
    fun `should save, find by memberId`() {
        val keyId = Uuids.timeBased()
        val membership = TopicMembershipByMember(
            Uuids.timeBased(),
            keyId,
            Uuids.timeBased())

        val membershipSave = byMemberRepo
            .save(membership)
            .thenMany(byMemberRepo.findByMember(keyId))

        StepVerifier
            .create(membershipSave)
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }

    // TODO Fix this testing bug (cassandraUnit based)
    //@Test
    fun `should save, find by memberOf`() {
        val keyId = Uuids.timeBased()
        val membership = TopicMembershipByMemberOf(
            Uuids.timeBased(),
            Uuids.timeBased(),
            keyId
        )

        val membershipSave = byMemberOfRepo
            .save(membership)
            .thenMany(byMemberOfRepo.findByMemberOf(keyId))

        StepVerifier
            .create(membershipSave)
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }

}