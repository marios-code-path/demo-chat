package com.demo.chat.config.rsocket

import com.demo.chat.config.PersistenceServiceBeans
//import com.demo.chat.config.secure.AuthBeansConfiguration
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
//import com.demo.chat.secure.service.CoreReactiveAuthenticationManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketMessageHandlerCustomizer
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.rsocket.RSocketSecurity
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver
import org.springframework.security.rsocket.api.PayloadExchange
import org.springframework.security.rsocket.api.PayloadInterceptor
import org.springframework.security.rsocket.api.PayloadInterceptorChain
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.web.util.pattern.PathPatternRouteMatcher
import reactor.core.publisher.Mono

@Configuration
@ConditionalOnProperty("app.server.proto", havingValue = "rsocket")
class RSocketServerConfiguration<T>(
    //private val coreBeans: PersistenceServiceBeans<T, *>,
  //  private val compositeBeans: AuthBeansConfiguration<T, *>
) {
//    @Bean
//    fun authMan(): ReactiveAuthenticationManager =
//        CoreReactiveAuthenticationManager(
//            compositeBeans.authenticationService(),
//            coreBeans.userPersistence()
//        )

    // TODO: lock down!
    @Bean
    fun rsocketSecurityAuthentication(
        security: RSocketSecurity,
        //authMan: ReactiveAuthenticationManager,
        rootKeys: RootKeys<T>
    )
            : PayloadSocketAcceptorInterceptor = security
        .simpleAuthentication(Customizer.withDefaults())
        //.authenticationManager(authMan)
        .addPayloadInterceptor(CustomAuthorizationPayloadInterceptor(rootKeys))
        .authorizePayload { authorize ->
            authorize
                .setup()
                .permitAll()
                .anyExchange()
                .permitAll()
                .anyRequest()
                .permitAll()

        }
        .build()

    @Bean
    fun rSocketStrategiesCustomizer(): RSocketStrategiesCustomizer =
        RSocketStrategiesCustomizer { strategies ->
            strategies.apply {
                encoder(SimpleAuthenticationEncoder())
                routeMatcher(PathPatternRouteMatcher())
            }
        }

    @Bean
    fun messageHandlerCustomizer(): RSocketMessageHandlerCustomizer =
        RSocketMessageHandlerCustomizer { messageHandler ->
            val ar: HandlerMethodArgumentResolver = AuthenticationPrincipalArgumentResolver()
            messageHandler.argumentResolverConfigurer.addCustomResolver(ar)
        }
}

class ChatAnonymousAuthenticationToken<T>(private var anonymousId: Key<T>) : AbstractAuthenticationToken(emptyList()) {
    override fun getCredentials(): Any = "ANONYMOUS"
    override fun getPrincipal(): Any = User
        .create(
            anonymousId,
            "AnonymousUser",
            "ANONYMOUS",
            "http://images/anonymous.png"
        )
}

// RootKeys gets instantiated too much later. this would need to hook into that lifecycle.
//object ChatAnonymousAuthenticationTokenFactory {
//    fun <T> create(anonymousId: Key<T>): AbstractAuthenticationToken {
//        return object : AbstractAuthenticationToken(emptyList()) {
//            override fun getCredentials(): Any = "ANONYMOUS"
//            override fun getPrincipal(): Any = User
//                .create(
//                    anonymousId,
//                    "AnonymousUser",
//                    "ANONYMOUS",
//                    "http://images/anonymous.png"
//                )
//        }
//    }
//}
class CustomAuthorizationPayloadInterceptor<T>(private val rootKeys: RootKeys<T>) : PayloadInterceptor {

    override fun intercept(exchange: PayloadExchange?, chain: PayloadInterceptorChain?): Mono<Void> {
        return ReactiveSecurityContextHolder
            .getContext()
            .filter { c: SecurityContext -> c.authentication != null }
            .doOnNext { con ->
                val anonymousId = rootKeys.getRootKey(Anon::class.java)
                when (con.authentication) {
                    is AnonymousAuthenticationToken -> con.authentication =
                        ChatAnonymousAuthenticationToken(anonymousId)
                    else -> {}
                }
            }
            .then(chain!!.next(exchange))
    }
}