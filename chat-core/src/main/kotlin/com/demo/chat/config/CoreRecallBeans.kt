package com.demo.chat.config

import com.demo.chat.service.vector.MessageRecallService

/**
 * The recall service of the active composition.
 *
 * Every controller takes this interface, and no controller takes
 * MessageRecallService by type. A controller that took the service by type
 * would meet two beans of that type on a classpath that carries both the
 * RSocket controllers and the REST controllers, because the RSocket controller
 * implements the service by delegation. The launch then fails with "expected
 * single matching bean but found 2".
 *
 * CompositeServiceBeans, PersistenceServiceBeans, IndexServiceBeans, and
 * PubSubServiceBeans all read the same way.
 */
interface CoreRecallBeans<T> {

    fun recallService(): MessageRecallService<T>
}
