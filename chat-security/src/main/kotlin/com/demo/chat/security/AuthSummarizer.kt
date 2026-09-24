package com.demo.chat.security

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.security.rank.PrincipalRank
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

class AuthSummarizer<T>(
    private val comparator: Comparator<AuthMetadata<T>>,
    private val principalRank: PrincipalRank<T>
) : Summarizer<AuthMetadata<T>, Key<T>> {

    companion object {
        /** The permission value that names every permission. */
        const val WILDCARD = "*"
    }

    /**
     * The rank rule, decided by the owner on 2026-09-24.
     *
     * The highest ranked row of a group decides, and its expiry is read after
     * it wins. Three levels, from the strongest.
     *
     * 1. A wildcard row beats a row that names one permission.
     * 2. `ENTITY` beats `DOMAIN_ROOT`.
     * 3. Later beats earlier. [comparator] supplies this level.
     *
     * Level 1 is why a close works. A close is one expired wildcard row on a
     * domain root principal, and it must remove a named grant whatever the
     * order of the two rows. Level 2 is why the owner and the administrator
     * survive that close, because each names an object principal.
     *
     * See `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`.
     */
    private val rank: Comparator<AuthMetadata<T>> =
        compareBy<AuthMetadata<T>> { meta -> if (meta.permission == WILDCARD) 1 else 0 }
            .thenBy { meta -> principalRank.of(meta.principal).ordinal }
            .thenComparing(comparator)

    /**
     * The group that a row competes in.
     *
     * A wildcard row competes in the group of the permission that the caller
     * asks, so the rank reads it beside the rows that name that permission.
     * The row keeps its own value here, because level 1 of the rank must still
     * see that it is a wildcard.
     */
    private fun groupKey(meta: AuthMetadata<T>, permission: String?): String = when {
        permission == null -> meta.permission
        meta.permission == WILDCARD -> permission
        else -> meta.permission
    }

    /**
     * Answer the winning row as the caller asked for it.
     *
     * A wildcard row that won takes the asked permission, because the caller
     * reads the permission of the row it receives. This runs after the rank,
     * so it never hides a wildcard from the rank.
     */
    private fun present(meta: AuthMetadata<T>, permission: String?): AuthMetadata<T> = when {
        permission == null -> meta
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
            .groupBy { g -> groupKey(g, permission) }
            .flatMap { g -> g.sort(rank).last() }
            .filter { meta -> (meta.expires == 0L || meta.expires > System.currentTimeMillis()) } // removing 0L allows us to overlay negative permission (+CREATE == Long.MAX_VALUE,  -CREATE = Long.MIN_VALUE)
            .map { meta -> present(meta, permission) }
}
