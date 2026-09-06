package com.demo.chat.config.auth

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.RequestToQueryConverters
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.service.CoreAuthBeans
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
class AuthBeansConfiguration<T, V, Q>(
    typeUtil: TypeUtil<T>,
    indexServices: IndexServiceBeans<T, V, Q>,
    queryConverters: RequestToQueryConverters<Q>,
    persistServices: PersistenceServiceBeans<T, V>,
    secretsStoreBeans: SecretsStoreBeans<T>,
    rootKeys: RootKeys<T>,
    passwordEncoder: PasswordEncoder
    //authSummarizer: Summarizer<AuthMetadata<T>, Key<T>>,
    ) : CoreAuthBeans<T, V, Q>(rootKeys,
    indexServices,
    persistServices,
    secretsStoreBeans,
    AuthSummarizer { a, b -> typeUtil.compare(a.key.id, b.key.id) },
    //authSummarizer,
    { key -> queryConverters.authPrincipalToQuery(ByIdRequest(key.id)) },
    { key -> queryConverters.authTargetToQuery(ByIdRequest(key.id)) },
    { username -> queryConverters.userHandleToQuery(ByStringRequest(username)) },
    { input, secure -> passwordEncoder.matches(input, secure) })
