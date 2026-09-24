package com.demo.chat.security.rank

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon
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
 * **A root key is not always a domain root.** `RootKeys` holds `Anon` and
 * `Admin` beside the domain roots, and each of those two names one user. They
 * are objects of the `User` domain, so this class reads them as `ENTITY`.
 *
 * That exclusion carries the administrator invariant. The row
 * `{ADMIN, ENTITY_ROOT, '*', never}` must outrank a close, and a close names a
 * domain root. A rank that read `RootKeys` membership alone would place the
 * administrator and the close at one level, and a closed target would then be
 * beyond administration.
 *
 * **The excluded set is closed.** Add a name only when that name stops being a
 * domain.
 */
class PrincipalRank<T>(
    private val rootKeys: RootKeys<T>,
    private val userObjects: Set<String> = setOf(
        Anon::class.java.simpleName,
        Admin::class.java.simpleName
    )
) {
    fun of(principal: Key<T>): PrincipalSpecificity = when {
        domainRoots().any { root -> root == principal } -> PrincipalSpecificity.DOMAIN_ROOT
        else -> PrincipalSpecificity.ENTITY
    }

    private fun domainRoots(): Collection<Key<T>> = rootKeys
        .getMapOfKeyMap()
        .filterKeys { name -> !userObjects.contains(name) }
        .values
}
