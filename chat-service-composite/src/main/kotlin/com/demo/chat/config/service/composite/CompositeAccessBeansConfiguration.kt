package com.demo.chat.config.service.composite

class CompositeAccessBeansConfiguration {
//
//@Configuration
//@ConditionalOnProperty("app.service.composite.security")
//class CompositeAccessBeansConfiguration<T>(
//    accessBroker: AccessBroker<T>,
//    rootKeys: RootKeys<T>,
//    compositeServiceBeansDefinition: CompositeServiceBeansDefinition<T, String, IndexSearchRequest>
//) : CompositeServiceBeans<T, String> by CompositeServiceAccessBeansConfiguration(
//    accessBroker = accessBroker,
//    principalKeyPublisher = {
//        ReactiveSecurityContextHolder.getContext()
//            .map { it.authentication.principal as ChatUserDetails<T> }
//            .map { it.user.key }
//    },
//    rootKeys = rootKeys,
//    compositeServiceBeansDefinition = compositeServiceBeansDefinition
//)
}