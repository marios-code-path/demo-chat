package com.demo.chat.controller.config.edge

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.UserIndexService
import org.springframework.messaging.handler.annotation.MessageMapping

@MessageMapping("user")
open class EdgeUserControllerConfiguration<T, V>(
    s: PersistenceServiceBeans<T, V>,
    x: IndexServiceBeans<T, V, IndexSearchRequest>,
) :
    UserServiceController<T, IndexSearchRequest>(
        userPersistence = s.userPersistence(),
        userIndex = x.userIndex(),
        userHandleToQuery = { r ->
            IndexSearchRequest(UserIndexService.HANDLE, r.handle, 100)
        })
