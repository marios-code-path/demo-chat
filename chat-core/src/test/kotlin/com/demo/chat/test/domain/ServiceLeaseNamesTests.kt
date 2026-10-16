package com.demo.chat.test.domain

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ServiceLeaseNames
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/** Lease names come from the present service beans. See `CHAT-lswjobhz`, plan review item 5. */
class ServiceLeaseNamesTests {

    private fun stub(type: Class<*>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, _, _ -> null }

    private val expected = listOf(
        UserPersistence::class.java to "persistence:user",
        TopicPersistence::class.java to "persistence:topic",
        MessagePersistence::class.java to "persistence:message",
        MembershipPersistence::class.java to "persistence:membership",
        AuthMetaPersistence::class.java to "persistence:authmeta",
        KeyValueStore::class.java to "persistence:keyvalue",
        UserIndexService::class.java to "index:user",
        TopicIndexService::class.java to "index:topic",
        MessageIndexService::class.java to "index:message",
        MembershipIndexService::class.java to "index:membership",
        AuthMetaIndex::class.java to "index:authmeta",
        KeyValueIndexService::class.java to "index:keyvalue",
    )

    @Test
    fun `each domain interface maps to its lease name`() {
        expected.forEach { (type, name) ->
            val service = stub(type)
            assertThat(ServiceLeaseNames.isStoreService(service)).describedAs(type.simpleName).isTrue()
            assertThat(ServiceLeaseNames.of(mapOf("bean" to service))).describedAs(type.simpleName).containsExactly(name)
        }
        assertThat(ServiceLeaseNames.ALL).containsExactlyInAnyOrderElementsOf(expected.map { it.second }).isSorted()
    }

    @Test
    fun `a store service with no domain interface fails and names the bean`() {
        assertThatThrownBy { ServiceLeaseNames.of(mapOf("orphanStore" to stub(PersistenceStore::class.java))) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("orphanStore")
            .hasMessageContaining("has no lease name")
    }

    @Test
    fun `two beans with one lease name claim it once, in sorted order`() {
        val names = ServiceLeaseNames.of(
            mapOf(
                "userPersistence" to stub(UserPersistence::class.java),
                "userPersistenceCopy" to stub(UserPersistence::class.java),
                "userIndex" to stub(UserIndexService::class.java),
            )
        )
        assertThat(names).containsExactly("index:user", "persistence:user")
    }

    @Test
    fun `a bean that is not a store service is not one`() {
        assertThat(ServiceLeaseNames.isStoreService("text")).isFalse()
    }

    @Test
    fun `the store services come from the holders, and a controller bean is not one`() {
        val holder = Proxy.newProxyInstance(
            PersistenceServiceBeans::class.java.classLoader, arrayOf(PersistenceServiceBeans::class.java)
        ) { _, method, _ -> stub(method.returnType) }
        val beans = org.springframework.beans.factory.support.StaticListableBeanFactory(
            mapOf(
                "redisPersistenceServices" to holder,
                "userPersistenceController" to stub(PersistenceStore::class.java),
                "other" to "text",
            )
        )
        val services = ServiceLeaseNames.servicesIn(beans)
        assertThat(services.keys).hasSize(6).allMatch { it.startsWith("redisPersistenceServices.") }
        assertThat(ServiceLeaseNames.of(services)).containsExactly(
            "persistence:authmeta", "persistence:keyvalue", "persistence:membership",
            "persistence:message", "persistence:topic", "persistence:user",
        )
    }
}
