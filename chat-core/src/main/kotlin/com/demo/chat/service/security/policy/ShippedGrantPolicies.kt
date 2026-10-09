package com.demo.chat.service.security.policy

/**
 * The grant policies that the shipped writers apply today.
 *
 * **No production code reads these policies yet.** `ContextRoomOwnerGrant` and
 * `MembershipGrant` still write their own rows. `CHAT-qojwcatx` moves both
 * writers onto these policies with no change in behaviour. Until then, a test
 * compares each policy with the rows that its writer writes.
 */
object ShippedGrantPolicies {

    const val SEND = "SEND"
    const val SUBSCRIBE = "SUBSCRIBE"

    /**
     * The creator of a room gets `*` on the room, with no expiry. `Anon` gets
     * no row. See `CHAT-zhjltbky`.
     */
    val ROOM_OWNER = GrantPolicy(
        name = "roomowner",
        trigger = ActionTrigger.ROOM_ADDED,
        rules = listOf(GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, GrantPolicy.WILDCARD, GrantExpiry.NONE)),
        noGrant = setOf(NoGrantPrincipal.ANON),
    )

    /**
     * The member of a join gets `SEND` and `SUBSCRIBE` on the room, with no
     * expiry. `Anon` and the `User` root get no row. See `CHAT-mfveaecc` and
     * `CHAT-lfaajjcj`.
     */
    val ROOM_JOIN = GrantPolicy(
        name = "roomjoin",
        trigger = ActionTrigger.ROOM_JOINED,
        rules = listOf(
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, SEND, GrantExpiry.NONE),
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, SUBSCRIBE, GrantExpiry.NONE),
        ),
        noGrant = setOf(NoGrantPrincipal.ANON, NoGrantPrincipal.USER_ROOT),
    )

    /**
     * The `SEND` and `SUBSCRIBE` grants of the member of a leave expire at the
     * time of the leave. `Anon` and the `User` root get no row.
     */
    val ROOM_LEAVE = GrantPolicy(
        name = "roomleave",
        trigger = ActionTrigger.ROOM_LEFT,
        rules = listOf(
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, SEND, GrantExpiry.NOW),
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, SUBSCRIBE, GrantExpiry.NOW),
        ),
        noGrant = setOf(NoGrantPrincipal.ANON, NoGrantPrincipal.USER_ROOT),
    )

    val ALL: List<GrantPolicy> = listOf(ROOM_OWNER, ROOM_JOIN, ROOM_LEAVE)
}
