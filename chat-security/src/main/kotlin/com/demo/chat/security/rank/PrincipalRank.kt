package com.demo.chat.security.rank

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys

/**
 * Level 2 of the rank rule. `ENTITY` beats `DOMAIN_ROOT`.
 *
 * `AuthSummarizer.WILDCARD` states what `*` means. Read it first. `*` is
 * ownership, it is singular per target, and it is a sentinel. This class
 * answers level 2, which decides only between two rows that tie at level 1.
 *
 * See `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`, under
 * `What \* means` and `The rank rule, decided by the owner on 2026-09-24`.
 */
enum class PrincipalSpecificity { DOMAIN_ROOT, ENTITY }

/**
 * Read the specificity of a principal.
 *
 * **A domain root is `DOMAIN_ROOT`. Every other principal is `ENTITY`.**
 * `RootKeys` holds the `Anon` and `Admin` identities apart from the domain
 * roots. Each identity names one user, so it is an object of the `User` domain
 * and reads as `ENTITY`.
 *
 * That separation carries the administrator invariant. The row
 * `{ADMIN, ENTITY_ROOT, '*', never}` must outrank a close, and a close names a
 * domain root. A rank that read the identities as domain roots would place the
 * administrator and the close at one level. A closed target would then be
 * beyond administration.
 *
 * Before `CHAT-avduuqwp`, `RootKeys` held the identities and the domain roots
 * in one map, and this class excluded the identity names from it. The typed
 * `RootKeys.domains()` now holds the domain roots alone.
 */
class PrincipalRank<T>(private val rootKeys: RootKeys<T>) {

    fun of(principal: Key<T>): PrincipalSpecificity = when {
        rootKeys.domains().values.any { root -> root == principal } -> PrincipalSpecificity.DOMAIN_ROOT
        else -> PrincipalSpecificity.ENTITY
    }
}
