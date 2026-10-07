package com.demo.chat.config.deploy.actuator

import com.demo.chat.service.core.IndexFileAdmin
import com.demo.chat.service.core.IndexFileReport
import com.demo.chat.service.core.IndexRequestResult
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.endpoint.Access
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.Selector
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * The operator view of the Lucene indexes, and the rebuild and drop requests.
 * A request takes effect at the next start. See CHAT-ybtirmgj.
 *
 * Access is NONE by default. Boot 4.0.8 defaults the annotation to
 * UNRESTRICTED, so this class sets NONE explicitly. An operator enables it
 * with two properties:
 *
 * ```
 * management.endpoint.luceneindex.access=unrestricted
 * management.endpoints.web.exposure.include=luceneindex
 * ```
 *
 * No deployment sets either. `ActuatorWebSecurityConfiguration` requires the
 * ACTUATOR role on every actuator route except health.
 *
 * The condition matches `LuceneIndexBeans`. A conditional on the registry bean
 * would depend on registration order, so the admin arrives through a provider.
 */
@Component
@Endpoint(id = "luceneindex", defaultAccess = Access.NONE)
@ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene", matchIfMissing = true)
class LuceneIndexEndpoint(private val admin: ObjectProvider<IndexFileAdmin>) {

    @ReadOperation
    fun reports(): List<IndexFileReport> = admin.ifAvailable?.reports() ?: emptyList()

    @WriteOperation
    fun rebuild(@Selector name: String): IndexRequestResult =
        admin.ifAvailable?.requestRebuild(name) ?: NO_INDEX

    @DeleteOperation
    fun drop(@Selector name: String): IndexRequestResult =
        admin.ifAvailable?.requestDrop(name) ?: NO_INDEX

    companion object {
        private val NO_INDEX = IndexRequestResult(false, "This process holds no Lucene index.", null)
    }
}
