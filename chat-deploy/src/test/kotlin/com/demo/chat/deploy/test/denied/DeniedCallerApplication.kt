package com.demo.chat.deploy.test.denied

import com.demo.chat.config.WebFluxSecurity
import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentIdentities
import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.demo.chat.config.agent.AgentResourceServerChain
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.User
import com.demo.chat.deploy.test.security.DeployTestSigningKey
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatMessageService
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.clearInvocations
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@SpringBootApplication(
    proxyBeanMethods = false,
    exclude = [RSocketSecurityAutoConfiguration::class],
)
@org.springframework.context.annotation.Import(DeniedCallerSecurityConfiguration::class)
class DeniedCallerApplication

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(AgentSecurityProperties::class)
class DeniedCallerSecurityConfiguration {

    @Bean
    @Suppress("UNCHECKED_CAST")
    fun chatMessageService(): ChatMessageService<Long, String> =
        mock(ChatMessageService::class.java) as ChatMessageService<Long, String>

    @Bean
    fun agentIdentities(): AgentIdentities = AgentIdentities().apply {
        resolve(
            mapOf(
                "client-under-test" to ChatUserDetails(
                    User.create(Key.of(7L, 1L), "agent-svc", "agent-svc", "http://agent-svc"),
                    emptyList<String>(),
                )
            )
        )
    }

    @Bean
    fun reactiveJwtDecoder(properties: AgentSecurityProperties): ReactiveJwtDecoder =
        AgentJwtDecoderFactory.fromJwkFile(properties.requireComplete().jwt.jwkPath)

    @Bean
    fun agentAuthenticationConverter(
        identities: AgentIdentities,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> = AgentAuthenticationConverter(identities)

    @Bean
    fun agentResourceServerChain(
        properties: AgentSecurityProperties,
        decoder: ReactiveJwtDecoder,
        converter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
    ): AgentResourceServerChain = AgentResourceServerChain(properties, decoder, converter)

    @Bean
    @Order(WebFluxSecurity.APPLICATION_CHAIN_ORDER)
    fun applicationFilterChain(
        http: ServerHttpSecurity,
        chain: AgentResourceServerChain,
    ): SecurityWebFilterChain = WebFluxSecurity(chain).filterChain(http)
}

@RestController
class DeniedCallerController(
    private val messaging: ChatMessageService<Long, String>,
) {

    init {
        `when`(messaging.send(MessageSendRequest("hello", 1L, 2L)))
            .thenReturn(Mono.just(Key.of(2L, 1L)))
        clearInvocations(messaging)
    }

    @PostMapping("/denied/send", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun send(@RequestBody body: String): Mono<String> =
        messaging.send(MessageSendRequest(body, 1L, 2L)).thenReturn("sent")
}
