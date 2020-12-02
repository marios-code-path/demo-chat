package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.deploy.config.core.PersistenceServiceFactory

open class UserControllerConfiguration<T, V, Q>(
        persistenceFactory: PersistenceServiceFactory<T, V>,
        indexFactory: IndexServiceFactory<T, V, Q>,
        reqs: RequestToQueryConverter<T, Q>,
) : UserServiceController<T, Q>(persistenceFactory.user(),
        indexFactory.userIndex(),
        reqs::userHandleToQuery)

