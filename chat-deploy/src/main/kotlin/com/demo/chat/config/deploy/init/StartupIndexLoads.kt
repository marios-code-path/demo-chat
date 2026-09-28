package com.demo.chat.config.deploy.init

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistedIndexLoad
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.StartupIndexLoad
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

/**
 * The index loads at start. See `CHAT-bafkgkko`.
 *
 * The Lucene index lives in process memory, so it is empty after a restart. A
 * grant read queries the auth index first. Without this load, a stored grant
 * is not found after a restart, even when the roots are stable.
 *
 * Only the auth index loads here. `CHAT-uxgdzpag` holds the other Lucene
 * indexes.
 */
@Configuration
class StartupIndexLoads {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * This load fills the Lucene auth index from the auth store. A
     * composition with no store or no index has nothing to load. Two
     * candidates of either kind fail the context, so the load never skips in
     * silence.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene", matchIfMissing = true)
    fun luceneAuthIndexLoad(
        persistence: ObjectProvider<PersistenceServiceBeans<*, *>>,
        index: ObjectProvider<IndexServiceBeans<*, *, *>>,
    ): StartupIndexLoad {
        val stores = persistence.ifAvailable
        val indexes = index.ifAvailable
        if (stores == null || indexes == null) {
            logger.info("This composition has no auth store or no auth index. The auth index load has nothing to load.")
            return StartupIndexLoad { Mono.empty() }
        }
        @Suppress("UNCHECKED_CAST")
        return PersistedIndexLoad(
            stores.authMetaPersistence() as PersistenceStore<Any?, AuthMetadata<Any?>>,
            indexes.authMetadataIndex() as IndexService<Any?, AuthMetadata<Any?>, Any?>,
        )
    }
}
