package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.secure.service.core.AuthorizationUtil
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import org.junit.jupiter.api.Test


class AuthorizationUtilTests(authMetaPersistence: PersistenceStore<Long, AuthMetadata<Long>>,
                             authMetaIndex: IndexService<Long, AuthMetadata<Long>, IndexSearchRequest>)
 : AuthorizationMetadataServiceTests(authMetaPersistence, authMetaIndex) {

     @Test
     fun `should not authorize for EXEC`() {
        // AuthorizationUtil(authSvc, { m -> m.key }, { m -> m.key })
     }
 }