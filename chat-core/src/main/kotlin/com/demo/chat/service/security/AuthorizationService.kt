package com.demo.chat.service.security

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.AuthorizationRequest
import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


/**
 * A service that determines when entity given Key<T> has Authorizations [M && N] where
 * T = Key Type
 * M = Authorization Type (out bound)
 * N = Authorization Type (in bound)
 */
interface AuthorizationService<T, out M : Any> {
    fun authorize(authorization: AuthMetadata<T>, exist: Boolean): Mono<Void>
    fun getAuthorizationsForTarget(uid: Key<T>): Flux<out M>
    fun getAuthorizationsForPrincipal(uid: Key<T>): Flux<out M>
    /**
     * Summarize the rows that decide one access question.
     *
     * Pass [permission] when the caller asks one permission. A row that holds
     * the wildcard then answers that permission. Leave it null to list the rows
     * as they are stored.
     */
    fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>, permission: String? = null): Flux<out M>
    fun getAuthorizationsAgainstMany(uidA: Key<T>, uidB: List<Key<T>>, permission: String? = null): Flux<out M>

    /**
     * The stored rows that name [principal] and [target] exactly.
     *
     * **This read does not summarize.** It keeps expired rows, it does not
     * rank, and it does not read the domain root of [target]. A writer that
     * changes one row uses it. An access check must not use it. See
     * `CHAT-mfveaecc`.
     */
    fun getStoredGrants(principal: Key<T>, target: Key<T>): Flux<out M>
}