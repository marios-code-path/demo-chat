package com.demo.chat.security

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import reactor.core.publisher.Flux


// DESCRIBE PERMISSIONS
// Access_BY_ID -> Object { ID | ROOTKEY }
// Objects
//  - User { ID | ROOTKEY }
//  - Message { ID | ROOTKEY }
//  - MessageTopic { ID | ROOTKEY }
//  - TopicMembership { ID | ROOTKEY }
//  - AuthMetadata { ID | ROOTKEY }

// If Principal == ROOTKeys[RootUser] the user is ROOT
// If Principal == ROOTKeys[AnonymousUser] the user is ANONYMOUS
// When this happens, all DOMAIN_ROOT permissions are always enforced

// Example User Topic Assignment
// Topic_KEY <- ANON_KEY => READ, SUBSCRIBE
// Topic_KEY <- OWNER_USER_KEY => ALL

// Example TopicMembership
// TopicMembership_KEY <- ANON_KEY => JOIN
// TopicMembership_KEY <- OWNER_USER_KEY => ALL
// TopicMembership_KEY <- MODERATOR_USER_KEY => JOIN, LEAVE, KICK, BAN, UNBAN, MUTE, UNMUTE, PROMOTE, DEMOTE

// POLICY for every object access:
// UNLESS [ROOT, ADMIN], root_permissions[rookKey].muted OR permissions[principal -> object].muted
// Expiration policy:  (decide left/rootKey or right/principalTargetPermission)
// {rootKey: Expired} | {permission: not expired} == not expired -> use permission
// {rootKey: not expired} | {permission: expired} == expired -> use root
// {rootKey: expired} | {permission: expired} == expired -> NONE
// {rootKey: not expired} | {permission: not expired} == not expired -> use permission
// Mute policy:
// {permission: not muted} == not muted
// {permission: muted} == muted

// 1: requires a principal entity id = Pid
// 2: requires all wildcard id's = Wid[n]
interface Summarizer<M : Any, T> {
    /**
     * Summarize the rows for one target.
     *
     * Pass [permission] when the caller asks one permission question. A row that
     * holds the wildcard then answers that permission. Leave it null to list the
     * rows as they are stored.
     */
    fun computeAggregates(elements: Flux<M>, actorIds: Sequence<T>, permission: String? = null): Flux<M>
}

class AuthSummarizer<T>(private val comparator: Comparator<AuthMetadata<T>>) : Summarizer<AuthMetadata<T>, Key<T>> {

    companion object {
        /** The permission value that names every permission. */
        const val WILDCARD = "*"
    }

    /**
     * Answer the row that [permission] must read.
     *
     * A wildcard row becomes a row for the asked permission. It then joins the
     * group of that permission, so the order and the expiry decide the answer.
     * Every other row stays as it is.
     */
    private fun expand(meta: AuthMetadata<T>, permission: String?): AuthMetadata<T> = when {
        permission == null -> meta
        permission == WILDCARD -> meta
        meta.permission != WILDCARD -> meta
        else -> AuthMetadata.create(meta.key, meta.principal, meta.target, permission, meta.mute, meta.expires)
    }

    override fun computeAggregates(
        elements: Flux<AuthMetadata<T>>,
        actorIds: Sequence<Key<T>>,
        permission: String?
    ): Flux<AuthMetadata<T>> =
        elements
            .filter { meta ->
                val principalId = meta.principal

                actorIds
                    .filter { targetId -> (targetId == principalId) }
                    .any()
            }
            .map { meta -> expand(meta, permission) }
            .groupBy { g -> g.permission }
            .flatMap { g -> g.sort(comparator).last() }
            .filter { meta -> (meta.expires == 0L || meta.expires > System.currentTimeMillis()) } // removing 0L allows us to overlay negative permission (+CREATE == Long.MAX_VALUE,  -CREATE = Long.MIN_VALUE)
}