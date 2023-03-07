package com.demo.chat.config.secure

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.AnonymousKey
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import java.util.function.Supplier

@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
class CompositeAuthConfiguration<T, V>(
    typeUtil: TypeUtil<T>,
    indexServices: IndexServiceBeans<T, V, IndexSearchRequest>,
    persistServices: PersistenceServiceBeans<T, V>,
    anonKeySupplier: Supplier<AnonymousKey<T>>,
    secretsStoreBeans: SecretsStoreBeans<T>,
    //authSummarizer: Summarizer<AuthMetadata<T>, Key<T>>,
) : BaseAuthConfiguration<T, V, IndexSearchRequest>(indexServices,
    persistServices,
    anonKeySupplier,
    secretsStoreBeans,
    AuthSummarizer { a, b -> typeUtil.compare(a.key.id, b.key.id) },
    //authSummarizer,
    { key -> IndexSearchRequest(AuthMetaIndex.PRINCIPAL, typeUtil.toString(key.id), 100) },
    { key -> IndexSearchRequest(AuthMetaIndex.TARGET, typeUtil.toString(key.id), 100) },
    { username -> IndexSearchRequest(UserIndexService.HANDLE, username, 1) },
    { input, secure -> input == secure })