package com.demo.chat.config.deploy.authserv

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.RequestToQueryConverters
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The application beans that this deployment owns.
 *
 * **This class exists because the deployment could not start without it.**
 * `AuthBeansConfiguration` in `chat-security` is
 * `@ConditionalOnProperty("app.service.composite.auth")`, and the launch sets
 * that property. Its constructor takes a [RequestToQueryConverters], and no
 * main source in this module supplied one. The context refresh failed with
 * "Parameter 2 of constructor in
 * com.demo.chat.config.auth.AuthBeansConfiguration required a bean of type
 * com.demo.chat.domain.RequestToQueryConverters that could not be found".
 *
 * **No test reported it, because the test supplied the bean itself.**
 * `AuthorizationServerDeployTests` declares a `@TestConfiguration` that
 * provides this exact bean, so `contextLoads` passed while the deployment
 * could not start. That is the pattern `CHAT-etfnihnu` recorded for the
 * embedding model. See CHAT-nfvielrm.
 *
 * The query type is [IndexSearchRequest], which is what
 * `chat-deploy-memory`, `chat-deploy-redis`, `chat-deploy-kafka` and
 * `chat-shell` each declare. The launch reaches the index over RSocket
 * through `app.client.rsocket.core.index`, and that client speaks the same
 * request type.
 */
@Configuration(proxyBeanMethods = false)
class AuthServerAppConfiguration {

    @Bean
    fun requestToQueryConverters(): RequestToQueryConverters<IndexSearchRequest> =
        IndexSearchRequestConverters()
}
