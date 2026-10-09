# Action grant policy execution

Issue: `CHAT-qojwcatx`. Model: `CHAT-xdsetfkf`, spec
`docs/superpowers/specs/2026-10-02-action-grant-policy-model-design.md`.

## Goal

Execute the grant policy model. Move `ContextRoomOwnerGrant` and
`MembershipGrant` onto it with no change in behaviour.

## Decisions

1. **One writer maps intents to `AuthMetadata` writes.** `PolicyGrantWriter`
   in `chat-security` holds the storage rules that `MembershipGrant` holds
   today. A `NONE` intent sets the expiry of each stored row of the same
   principal, target, and permission to `0`. It writes one new row when no
   such row exists. A `NOW` intent sets each live row to expire at the action
   time. It writes no row when no live row exists.
2. **The composite keeps its two ports.** `TopicServiceImpl` calls
   `RoomOwnerGrant` and `RoomMemberGrant` as it does today. The owner decided
   on 2026-10-08 that the composite calls the policy directly, as the two
   writers do. Each port implementation applies one named policy.
3. **The active principal.** `ContextRoomOwnerGrant` resolves it through
   `ContextIdentity`. `MembershipGrant` uses the verified member of the
   request. A member can differ from the caller when the caller holds `JOIN`
   on the member, so this keeps the shipped behaviour.
4. **A policy has no effect until it is enabled.** `PolicyGrantWriter` takes
   an explicit list of enabled policies. It writes nothing for a policy that
   is not in that list. The configuration enables `ShippedGrantPolicies.ALL`.
5. **Each write mints its own grant key.** The new row carries an empty key
   with the `AUTH_METADATA` root, and the store mints the key. A target key
   never becomes a grant row key.
6. **No authorization topic.** The implementation does not need one.

## Tasks

1. `PolicyGrantWriter` with tests for principal, target, permission, expiry,
   enablement, and key root separation.
2. `MembershipGrant` and `ContextRoomOwnerGrant` delegate to it. Their
   constructors keep their parameters.
3. One configuration bean for the writer. Both grant configurations use it.
4. Run the five named tests with no change to their assertions:
   `AnonymousAuthorizationMatrixTests`, `RoomOwnerGrantTests`,
   `MembershipGrantTests`, `TopicServiceMemberGrantTests`, and
   `StandardUserJoinSendTests`.
5. Update the spec, the forward register, and the gates.
