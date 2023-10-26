package com.demo.chat.config.secure

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.secure.service.CoreAuthBeans
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
class AuthBeansConfiguration<T, V>(
    typeUtil: TypeUtil<T>,
    indexServices: IndexServiceBeans<T, V, IndexSearchRequest>,
    persistServices: PersistenceServiceBeans<T, V>,
    secretsStoreBeans: SecretsStoreBeans<T>,
    rootKeys: RootKeys<T>,
    passwordEncoder: PasswordEncoder
    //authSummarizer: Summarizer<AuthMetadata<T>, Key<T>>,
) : CoreAuthBeans<T, V, IndexSearchRequest>(rootKeys,
    indexServices,
    persistServices,
    secretsStoreBeans,
    AuthSummarizer { a, b -> typeUtil.compare(a.key.id, b.key.id) },
    //authSummarizer,
    { key -> IndexSearchRequest(AuthMetaIndex.PRINCIPAL, typeUtil.toString(key.id), 100) },
    { key -> IndexSearchRequest(AuthMetaIndex.TARGET, typeUtil.toString(key.id), 100) },
    { username -> IndexSearchRequest(UserIndexService.HANDLE, username, 1) },
    { input, secure -> passwordEncoder.matches(input, secure) })