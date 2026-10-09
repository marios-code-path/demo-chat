package com.demo.chat.service.security.policy

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys

/**
 * The operation whose completion fires a grant policy.
 *
 * **A trigger names an operation. It does not enable a policy.** No production
 * code reads a trigger yet. `CHAT-qojwcatx` defines how a completed operation
 * calls a policy.
 */
enum class ActionTrigger {
    /** A room is added. */
    ROOM_ADDED,

    /** A member joins a room. */
    ROOM_JOINED,

    /** A member leaves a room. */
    ROOM_LEFT,
}

/**
 * The runtime source of the principal of one grant.
 *
 * **These are runtime sources and not configuration names.** Root key
 * configuration accepts a domain name, `Admin` or `Anon`. See `CHAT-zcxgrtqc`.
 */
enum class PrincipalSource {
    /**
     * The principal that the completed operation acts for. That is the creator
     * of a room, or the member of a join or a leave. An operation with no such
     * principal gives no grant from this source.
     */
    ACTIVE,

    /** The root key of the `User` domain. */
    ROOT,
}

/** The runtime source of the target of one grant. */
enum class TargetSource {
    /**
     * The room key that the completed operation names. The policy never
     * replaces it with the domain root, because that would widen the grant.
     */
    ROOM,
}

/**
 * The expiry of one grant intent, as a policy value.
 *
 * **This is not a stored value.** The storage of an expiry belongs to
 * `CHAT-qojwcatx`. Seed rows carry no expiry. See `CHAT-zcxgrtqc`.
 */
enum class GrantExpiry {
    /** The grant does not expire. */
    NONE,

    /**
     * The grant expires at the time of the action. Every earlier grant of the
     * same principal, target and permission ends at that time. A leave uses it.
     */
    NOW,
}

/**
 * A principal that receives no grant from a policy.
 *
 * Both keys are in the actor set of every query. A grant for either key would
 * reach every caller.
 */
enum class NoGrantPrincipal {
    /** The `Anon` identity. */
    ANON,

    /** The root key of the `User` domain. */
    USER_ROOT,
}

/** One grant of a policy, as sources. [GrantPolicy.intents] resolves it. */
data class GrantRule(
    val principal: PrincipalSource,
    val target: TargetSource,
    val permission: String,
    val expiry: GrantExpiry,
)

/**
 * One logical grant that a policy gives for one completed action.
 *
 * **An intent carries no storage key.** It names the principal, the target, the
 * permission and the expiry. The writer of `CHAT-qojwcatx` selects the key of
 * each grant row. A target key never becomes a grant row key.
 */
data class GrantIntent<T>(
    val principal: Key<T>,
    val target: Key<T>,
    val permission: String,
    val expiry: GrantExpiry,
)

/**
 * The values of one completed operation that a policy reads.
 *
 * [active] is null when the operation acts for no principal. A caller with no
 * identity is an example.
 */
data class ActionContext<T>(
    val active: Key<T>?,
    val room: Key<T>,
)

/**
 * A named grant policy that follows one completed action.
 *
 * The policy returns logical grant intents. It reads no store and writes no
 * row. One action can give more than one intent.
 *
 * The constructor refuses a policy that the owner rules do not allow:
 *
 * - A rule with the permission `-`. Replacement semantics do not support
 *   subtraction. See `CHAT-zcxgrtqc`.
 * - A `*` rule other than the room owner rule. `*` comes from three sources
 *   only, and the room owner row is the only one that an action writes.
 * - Two rules for one principal source, target source and permission.
 * - A `ROOT` rule in a policy that gives the `User` root no grant.
 */
class GrantPolicy(
    val name: String,
    val trigger: ActionTrigger,
    val rules: List<GrantRule>,
    val noGrant: Set<NoGrantPrincipal>,
) {
    init {
        require(name.isNotBlank()) { "A grant policy needs a name." }
        require(rules.isNotEmpty()) { "Grant policy '$name' needs at least one rule." }
        rules.forEach { rule -> checkRule(rule) }
        val repeated = rules.groupBy { Triple(it.principal, it.target, it.permission) }.filterValues { it.size > 1 }.keys
        require(repeated.isEmpty()) { "Grant policy '$name' repeats a rule: $repeated" }
    }

    /**
     * This method returns the intents of this policy for one completed action.
     *
     * A rule gives no intent when its principal source resolves to no
     * principal, or to a principal in [noGrant].
     */
    fun <T> intents(context: ActionContext<T>, rootKeys: RootKeys<T>): List<GrantIntent<T>> {
        val refused = noGrant.map { principal ->
            when (principal) {
                NoGrantPrincipal.ANON -> rootKeys.anon()
                NoGrantPrincipal.USER_ROOT -> rootKeys.of(ChatDomain.USER)
            }
        }.toSet()
        return rules.mapNotNull { rule ->
            val principal = when (rule.principal) {
                PrincipalSource.ACTIVE -> context.active
                PrincipalSource.ROOT -> rootKeys.of(ChatDomain.USER)
            }
            val target = when (rule.target) {
                TargetSource.ROOM -> context.room
            }
            if (principal == null || principal in refused) null
            else GrantIntent(principal, target, rule.permission, rule.expiry)
        }
    }

    private fun checkRule(rule: GrantRule) {
        require(rule.permission.isNotBlank()) { "Grant policy '$name' has a rule with no permission." }
        require(rule.permission != SUBTRACT) {
            "Grant policy '$name' uses the permission '$SUBTRACT'. A grant cannot remove a permission."
        }
        if (rule.permission == WILDCARD) require(isOwnerRule(rule)) {
            "Grant policy '$name' writes '$WILDCARD' outside the room owner rule. " +
                "Only $ROOM_OWNER_RULE may write '$WILDCARD'."
        }
        require(!(rule.principal == PrincipalSource.ROOT && NoGrantPrincipal.USER_ROOT in noGrant)) {
            "Grant policy '$name' has a ROOT rule and gives the User root no grant."
        }
    }

    private fun isOwnerRule(rule: GrantRule): Boolean =
        trigger == ActionTrigger.ROOM_ADDED &&
            rule.principal == PrincipalSource.ACTIVE &&
            rule.target == TargetSource.ROOM &&
            rule.expiry == GrantExpiry.NONE &&
            NoGrantPrincipal.ANON in noGrant

    override fun toString(): String = "GrantPolicy($name, $trigger, $rules, noGrant=$noGrant)"

    companion object {
        /** The permission that holds every permission. `AuthSummarizer.WILDCARD` has the same value. */
        const val WILDCARD = "*"

        /** The spelling that the owner rejected. See `CHAT-zcxgrtqc`. */
        const val SUBTRACT = "-"

        private const val ROOM_OWNER_RULE =
            "a ROOM_ADDED rule with an ACTIVE principal, a ROOM target, no expiry, and no grant for Anon"
    }
}
