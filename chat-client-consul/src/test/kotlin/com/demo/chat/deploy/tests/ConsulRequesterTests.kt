package com.demo.chat.deploy.tests

import com.demo.chat.client.rsocket.ConsulDiscoveryRequesterFactory
import com.demo.chat.config.client.rsocket.ConsulRequesterFactoryConfiguration
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ConsulRequesterFactoryConfiguration::class, )
@TestPropertySource(properties = ["app.rsocket.client.requester.factory=consul"])
class ConsulRequesterTests {

    @MockBean
    private lateinit var builder: RSocketRequester.Builder

    @MockBean
    private lateinit var discovery: ConsulReactiveDiscoveryClient

    @MockBean
    private lateinit var configProps: ClientProperties<ClientProperty>

    @MockBean
    private lateinit var connection: ClientTransportFactory<ClientTransport>

    @Autowired
    private lateinit var facotry: ConsulDiscoveryRequesterFactory

    @Test
    fun `get consulFactory`() {

    }
}