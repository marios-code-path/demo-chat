package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.deploy.config.codec.RequestToQueryConverters
import com.demo.chat.deploy.config.core.IndexServiceConfiguration
import com.demo.chat.deploy.config.core.PersistenceServiceConfiguration

open class UserControllerConfiguration<T, V, Q>(
        persistenceFactory: PersistenceServiceConfiguration<T, V>,
        indexFactory: IndexServiceConfiguration<T, V, Q>,
        reqs: RequestToQueryConverters<T, Q>,
) : UserServiceController<T, Q>(persistenceFactory.user(),
        indexFactory.userIndex(),
        reqs::userHandleToQuery)

