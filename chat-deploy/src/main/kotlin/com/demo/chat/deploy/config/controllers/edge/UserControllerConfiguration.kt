package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.deploy.config.core.PersistenceServiceFactory
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*

open class UserControllerConfiguration {

    //@Controller
    //@MessageMapping("edge.user")
    class UserController<T, V, Q>(
            persistenceFactory: PersistenceServiceFactory<T, V>,
            indexFactory: IndexServiceFactory<T, V, Q>,
            reqs: RequestToQueryConverter<T, Q>,
    ) : UserServiceController<T, Q>(persistenceFactory.user(),
            indexFactory.userIndex(),
            reqs::userHandleToQuery)
}
