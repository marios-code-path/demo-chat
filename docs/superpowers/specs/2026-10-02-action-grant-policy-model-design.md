# Action-triggered grant policy model

Issue: `CHAT-xdsetfkf`. Corrected on 2026-10-08 against master `e66d2257`.

## Purpose

This document defines a typed policy model for grant work that follows a named
operational action.

The model does not enable a permission. No production code reads it yet.
`CHAT-qojwcatx` defines execution and storage, and it moves the shipped writers
onto the model.

## Correction of 2026-10-08

The first version of this document predates the join grant. Two statements in
it were stale:

1. It used `joinsend` as an example only. A join writes `SEND` and `SUBSCRIBE`
   since PR #169 and PR #173. That is shipped behaviour.
2. It said that `joinRoom` does not change. `joinRoom` calls `RoomMemberGrant`
   since PR #169.

The model now states the shipped writers as its first three policies.

## The shipped policies

Two hard-coded writers apply these policies today.

| Policy | Trigger | Writer | Grant |
|---|---|---|---|
| `roomowner` | `ROOM_ADDED` | `ContextRoomOwnerGrant` | The creator gets `*` on the room, with no expiry. `Anon` gets no row. |
| `roomjoin` | `ROOM_JOINED` | `MembershipGrant` | The member gets `SEND` and `SUBSCRIBE` on the room, with no expiry. `Anon` and the `User` root get no row. |
| `roomleave` | `ROOM_LEFT` | `MembershipGrant` | The `SEND` and `SUBSCRIBE` rows of the member expire at the leave time. `Anon` and the `User` root get no row. |

Sources: `CHAT-zhjltbky` for the owner row, `CHAT-mfveaecc` for `SEND`, and
`CHAT-lfaajjcj` for `SUBSCRIBE`.

`ShippedGrantPolicies` in `chat-core` holds the three policies.
`ShippedGrantPolicyTests` in `chat-security` runs each production writer
against a map store. It compares the stored rows with the policy intents for
the same action.

## The model

The types are in `chat-core`, package
`com.demo.chat.service.security.policy`.

| Type | Values | Meaning |
|---|---|---|
| `ActionTrigger` | `ROOM_ADDED`, `ROOM_JOINED`, `ROOM_LEFT` | The operation whose completion fires a policy |
| `PrincipalSource` | `ACTIVE`, `ROOT` | The runtime source of the grant principal |
| `TargetSource` | `ROOM` | The runtime source of the grant target |
| `GrantExpiry` | `NONE`, `NOW` | The expiry of one intent, as a policy value |
| `NoGrantPrincipal` | `ANON`, `USER_ROOT` | A principal that receives no grant from a policy |
| `GrantRule` | principal source, target source, permission, expiry | One grant of a policy, as sources |
| `GrantPolicy` | name, trigger, rules, no-grant set | One named policy |
| `ActionContext` | active principal or null, room | The values of one completed operation |
| `GrantIntent` | principal, target, permission, expiry | One logical grant |

`GrantPolicy.intents` resolves each rule for one `ActionContext`. It returns a
list of `GrantIntent` values. It reads no store and writes no row.

### Principal sources

- `ACTIVE` is the principal that the completed operation acts for. That is the
  creator of a room, or the member of a join or a leave.
- `ROOT` is the root key of the `User` domain.

These are runtime sources. They are not configuration names. Root key
configuration accepts an exact domain name, `Admin`, or `Anon`. The owner
decided this on 2026-10-08, under `CHAT-zcxgrtqc`.

A rule gives no intent when its principal source resolves to no principal. A
caller with no identity is an example. That is not an error.

### The target source

`ROOM` is the room key that the completed operation names. The model never
replaces an object target with a domain root, because that would widen the
grant.

### Expiry

- `NONE`: the grant does not expire.
- `NOW`: the grant expires at the time of the action. Every earlier grant of
  the same principal, target, and permission ends at that time. A leave uses it.

The owner decided these two values on 2026-10-08. They exist only in this
model. Seed rows in `userinit.yml` carry no expiry.

An intent states the grant that holds after the action. The mapping of an
expiry value to a stored `expires` value belongs to `CHAT-qojwcatx`. The shipped
writers store `NONE` as `0`, and `NOW` as the clock time of the leave.

### Principals that get no grant

`ANON` and `USER_ROOT` are in the actor set of every query. A grant for either
key would reach every caller. A policy names the principals that get no grant
in its no-grant set. `GrantPolicy.intents` drops an intent whose resolved
principal is in that set.

## The rules that the constructor enforces

`GrantPolicy` refuses these policies at construction:

1. A rule with the permission `-`. Replacement semantics do not support
   subtraction. Owner decision of 2026-10-08.
2. A `*` rule other than the room owner rule. The room owner rule has the
   trigger `ROOM_ADDED`, an `ACTIVE` principal, a `ROOM` target, the expiry
   `NONE`, and `ANON` in the no-grant set.
3. Two rules with one principal source, target source, and permission.
4. A `ROOT` rule in a policy that gives the `User` root no grant.
5. A policy with no name, or with no rule.

Rule 2 follows the owner decision of 2026-10-08. `*` comes from three sources
only: the seed rows, the generated `Admin` rows, and the room owner row. Only
the room owner row comes from an action. The draft close row
`{User{ID=ROOT}, key.id, '*', now}` is refused. A close policy needs a new
owner decision before rule 2 can change.

## Key rule

A `GrantIntent` carries no storage key. The model does not select a
persistence key, and it does not reuse one.

`AuthMetadata` keys use the `AUTH_METADATA` root. Topic keys use the
`MESSAGE_TOPIC` root. Key equality includes the root, and the key registry
records the root for each generated identifier.

The implementation of `CHAT-qojwcatx` must keep the topic key and the grant row
key independent. A topic can reference a grant row. A topic must not become
the grant row.

## Deferred to `CHAT-qojwcatx`

- How a completed operation calls a policy. The owner decided on 2026-10-08
  that the composite service calls the policy directly.
- The mapping of each intent to `AuthMetadata` writes.
- The resolution of `ACTIVE` from the authenticated operation context.
- The move of `ContextRoomOwnerGrant` and `MembershipGrant` onto the model,
  with no change in behaviour.
- Failure behaviour. This model does not choose compensation or retry.

## Acceptance

- The model names action triggers and does not enable them.
- Each grant intent carries principal, target, permission, and expiry.
- The model states the add, join, and leave policies. A test compares each
  policy with the rows that its shipped writer writes.
- The model does not select or reuse persistence keys.
- No production permission changes.
