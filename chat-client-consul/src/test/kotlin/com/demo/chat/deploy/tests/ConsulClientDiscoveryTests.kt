package com.demo.chat.deploy.tests

import com.demo.chat.client.discovery.ConsulClientDiscovery
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.DiscoveryException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

// The shell names one core with --core-id. The discovery keeps the instance
// whose nodeId metadata matches. See CHAT-yexqiyie.
class ConsulClientDiscoveryTests {

    // One fake. ReactiveDiscoveryClient is an interface, so no mock is needed.
    // A set failure makes the lookup fail, as an unreachable registry does.
    private class FakeDiscovery(
        private val instances: List<ServiceInstance>,
        private val failure: Throwable? = null,
    ) : ReactiveDiscoveryClient {
        override fun description() = "fake"
        override fun getInstances(serviceId: String): Flux<ServiceInstance> =
            failure?.let { Flux.error(it) } ?: Flux.fromIterable(instances)
        override fun getServices(): Flux<String> = Flux.just(SERVICE)
    }

    private val props = object : ClientProperties<ClientProperty> {
        override fun getServiceConfig(serviceKey: String): ClientProperty =
            ClientProperty.create(SERVICE, "key.")
    }

    private fun instance(
        nodeId: String?,
        host: String = "172.26.0.5",
        port: Int = 6790,
        advertiseHost: String? = null,
        advertisePort: String? = null,
    ): ServiceInstance {
        val metadata = mutableMapOf("keyType" to "uuid")
        nodeId?.let { metadata["nodeId"] = it }
        advertiseHost?.let { metadata["advertiseHost"] = it }
        advertisePort?.let { metadata["advertisePort"] = it }
        return DefaultServiceInstance("$SERVICE-$nodeId", SERVICE, host, port, false, metadata)
    }

    // retryCount 0 keeps a failing test fast. The production default is 10.
    private fun discovery(
        instances: List<ServiceInstance>,
        coreId: Int? = null,
        registry: String = REGISTRY,
    ) = ConsulClientDiscovery(FakeDiscovery(instances), props, coreId, registry, retryCount = 0)

    @Test
    fun noCoreIdKeepsTheFirstInstance() {
        val found = discovery(
            listOf(instance("16", host = "10.0.0.1"), instance("9", host = "10.0.0.9")),
        ).getServiceInstance("key").block()!!

        assertEquals("10.0.0.1", found.host)
    }

    @Test
    fun coreIdKeepsTheMatch() {
        val found = discovery(
            listOf(instance("16", host = "10.0.0.16"), instance("9", host = "10.0.0.9")),
            coreId = 9,
        ).getServiceInstance("key").block()!!

        assertEquals("9", found.metadata!!["nodeId"])
        assertEquals("10.0.0.9", found.host)
    }

    @Test
    fun noMatchFails() {
        StepVerifier
            .create(discovery(listOf(instance("16")), coreId = 9).getServiceInstance("key"))
            .expectErrorMatches { error ->
                error is DiscoveryException &&
                    error.message!!.contains(SERVICE) &&
                    error.message!!.contains("9") &&
                    error.message!!.contains(REGISTRY)
            }
            .verify()
    }

    @Test
    fun advertiseMetadataReplacesTheAddress() {
        val found = discovery(
            listOf(instance("9", advertiseHost = "127.0.0.1", advertisePort = "6890")),
            coreId = 9,
        ).getServiceInstance("key").block()!!

        assertEquals("127.0.0.1", found.host)
        assertEquals(6890, found.port)
        assertEquals("9", found.metadata!!["nodeId"])
    }

    @Test
    fun noAdvertiseMetadataKeepsTheRegisteredAddress() {
        val found = discovery(listOf(instance("9")), coreId = 9)
            .getServiceInstance("key").block()!!

        assertEquals("172.26.0.5", found.host)
        assertEquals(6790, found.port)
    }

    @Test
    fun anInstanceWithoutANodeIdDoesNotMatch() {
        val found = discovery(
            listOf(instance(null, host = "10.0.0.1"), instance("9")),
            coreId = 9,
        ).getServiceInstance("key").block()!!

        assertEquals("172.26.0.5", found.host)
    }

    @Test
    fun aBadAdvertisePortKeepsTheRegisteredPort() {
        val found = discovery(
            listOf(instance("9", advertiseHost = "127.0.0.1", advertisePort = "abc")),
            coreId = 9,
        ).getServiceInstance("key").block()!!

        assertEquals("172.26.0.5", found.host)
        assertEquals(6790, found.port)
    }

    // A port outside 1..65535 cannot hold a connection. See CHAT-yexqiyie.
    @Test
    fun anOutOfRangeAdvertisePortKeepsTheRegisteredPort() {
        val found = discovery(
            listOf(instance("9", advertiseHost = "127.0.0.1", advertisePort = "70000")),
            coreId = 9,
        ).getServiceInstance("key").block()!!

        assertEquals("172.26.0.5", found.host)
        assertEquals(6790, found.port)
    }

    @Test
    fun aZeroAdvertisePortKeepsTheRegisteredPort() {
        val found = discovery(
            listOf(instance("9", advertiseHost = "127.0.0.1", advertisePort = "0")),
            coreId = 9,
        ).getServiceInstance("key").block()!!

        assertEquals(6790, found.port)
    }

    // An unreachable registry must name the registry. Otherwise the caller
    // sees a bare client error. See CHAT-yexqiyie.
    @Test
    fun anUnreachableRegistryNamesTheRegistry() {
        val failing = ConsulClientDiscovery(
            FakeDiscovery(emptyList(), IllegalStateException("Connection refused")),
            props, 9, REGISTRY, retryCount = 0,
        )

        StepVerifier
            .create(failing.getServiceInstance("key"))
            .expectErrorMatches { error ->
                error is DiscoveryException &&
                    error.message!!.contains(REGISTRY) &&
                    error.message!!.contains("Connection refused")
            }
            .verify()
    }

    companion object {
        private const val SERVICE = "core-service-rsocket"
        private const val REGISTRY = "127.0.0.1:6892"
    }
}
