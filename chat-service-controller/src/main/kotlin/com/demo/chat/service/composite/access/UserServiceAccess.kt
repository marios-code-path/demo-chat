package com.demo.chat.service.composite.access

import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.secure.access.AccessBroker
import com.demo.chat.service.composite.ChatUserService
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class UserServiceAccess<T>(
    private val authMetadataAccessBroker: AccessBroker<T>,
    private val principalSupplier: () -> Publisher<Key<T>>,
    private val rootKeys: RootKeys<T>,
    private val that: ChatUserService<T>
) : ChatUserService<T> {
    override fun addUser(userReq: UserCreateRequest): Mono<out Key<T>> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), rootKeys.getRootKey(User::class.java), "CREATE")
        .then(that.addUser(userReq))

    override fun findByUsername(req: ByStringRequest): Flux<out User<T>> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), rootKeys.getRootKey(User::class.java), "READ")
        .thenMany(that.findByUsername(req))

    override fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), Key.funKey(req.id), "READ")
        .then(that.findByUserId(req))
}