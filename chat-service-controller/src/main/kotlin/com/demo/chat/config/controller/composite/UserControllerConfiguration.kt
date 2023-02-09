package com.demo.chat.config.controller.composite

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.composite.UserServiceController
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.core.UserIndexService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@ConditionalOnProperty(prefix = "app.service.composite", name = ["user"])
@Controller
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
