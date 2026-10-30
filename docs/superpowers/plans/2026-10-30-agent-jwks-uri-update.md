# A changed agent JWKS URI reaches a running deployment: implementation plan

> Implement this plan with one agent. Track progress in `CHAT-vidnpywh`.
> The repository forbids subagent development and Markdown task checkboxes.

**Goal:** Let a relaunch apply a changed agent JWKS URI, and keep every other drift refusal.

**Architecture:** `AgentClients.reconcile` owns the decision and the save.
`AgentClients.differences` does not change.
The update derives from the stored row, so the row id never changes.

**Tools:** Kotlin, Mockito, `just build-partial`, and `drift`.

**Basis:** [Approved design](../specs/2026-10-30-agent-jwks-uri-update-design.md), commit `da829b7e`.
Approved on 2026-10-30 for planning. Implementation needs its own approval.

**Baselines at `da829b7e`:** `AgentClientsTests` holds 17 cases.
`just build-partial chat-authorization-server unit` reports 88 tests and 0 failures.
GitHub run `41022623456` reports the same 88 on commit `6f321b43`.

## File responsibilities

| File | Responsibility |
|---|---|
| `chat-authorization-server/src/main/kotlin/com/demo/chat/config/deploy/authserv/AgentClients.kt` | Update the stored client when the URI is the only difference. Print the change. |
| `chat-authorization-server/src/test/kotlin/com/demo/chat/AgentClientsTests.kt` | Hold every case of the design verification list. |
| `docs/MCP-CREDENTIAL-ISSUANCE.md` | State that a recreate now applies a URI change. |
| `shell-scripts/README-chat-build.md` | Carry the same change in short form. |

`AgentClientProperties.kt` does not change.
`ClientLoader.kt` does not change.
`requireRowFree` does not change, and the update path adds no call to it.
`differences` does not change, so `JWK Set URI` keeps its name and every other field keeps its rule.

## The change in `reconcile`

The new branch sits between the drift computation and the drift throw.

```kotlin
val drift = differences(row, scope, agent.jwkSetUri)
if (drift == listOf("JWK Set URI")) {
    val updated = RegisteredClient.from(row)
        .clientSettings(
            ClientSettings.withSettings(row.clientSettings.settings)
                .jwkSetUrl(agent.jwkSetUri)
                .build()
        )
        .build()
    repository.save(updated)
    out("Private_key_jwt agent client '${agent.clientId}' (${agent.username}) " +
        "changed its JWKS URI from '${row.clientSettings.jwkSetUrl}' to '${agent.jwkSetUri}'.")
    return@forEach
}
if (drift.isNotEmpty()) {
    // the present throw, unchanged
}
```

`drift == listOf("JWK Set URI")` holds for that one difference alone.
Two or more differences keep the throw, because the list then holds another name.

`agent.jwkSetUri` is not null inside that branch.
`differences` adds `JWK Set URI` only under `jwkSetUri != null`.
A mapping without a URI against a stored private key client names `authentication method`
and `secret prefix`, so that case keeps the throw.

Read the old URI from `row` before the save, because the save replaces the row.

## Task 1: Add every case of the verification list

**Files:** `chat-authorization-server/src/test/kotlin/com/demo/chat/AgentClientsTests.kt`.

1. Add these nine cases before any production change. Each one fails.

   1. `reconcile` saves an updated client when only the URI differs.
   2. The saved client holds the new URI.
   3. The printed line names the old URI and the new URI.
   4. A stored client whose row id differs from its client id keeps that row id.
      Build it with `RegisteredClient.withId("row-9")` and `.clientId("client-a")`.
      Assert the saved client `id` equals `row-9`.
      This case is required. It guards against a client built from the mapping.
   5. The update keeps the consent flag and the token endpoint authentication signing algorithm.
   6. The update keeps the stored token settings and the stored scope.
   7. `reconcile` still throws when the URI and the scope both differ.
   8. A stored secret client against a JWKS mapping still throws.
   9. A stored private key client against a secret mapping still throws.

   Capture the saved client with `Mockito.ArgumentCaptor`, as cases 1 to 6 need its fields.

2. Run the focused class and record this sequence:

   | Case group | Before the change | Reason |
   | --- | --- | --- |
   | 1 to 6 | fail | `reconcile` throws instead of saving |
   | 7 to 9 | pass | two or more differences already throw |

   Cases 1 to 6 are the failing-test-first evidence. Record each failure.
   Cases 7 to 9 are controls. They must pass before the change and after it.
   A control that fails before the change means its fixture is wrong. Repair the fixture first.

3. Do not change `a changed JWKS URI fails the start and names the repair` yet.
   That case asserts the old behavior, and Task 2 replaces it.
   Do not change `a changed JWKS URI is drift, so a relaunch alone cannot apply it`.
   Its name states a conclusion that Task 2 corrects, and its assertion stays true.
   Do not change `an agent that leaves the mapping keeps its stored client`.

## Task 2: Update the stored client

**Files:** `AgentClients.kt`, `AgentClientsTests.kt`.

1. Add the branch shown above to `reconcile`.
   Place it before the drift throw.
   Keep the throw text and the throw path unchanged.
2. Replace `a changed JWKS URI fails the start and names the repair` in the same step.
   It asserted the old behavior, so it cannot stay. Name the new case for the new behavior.
   State in its KDoc that commit `6f321b43` added the old case and this design replaced it.
3. Rename `a changed JWKS URI is drift, so a relaunch alone cannot apply it`.
   Its assertion on `differences` stays. Its name must no longer claim a relaunch cannot apply it.
4. Update the class KDoc that calls the three cases a record of today's behavior.
   One of them now records the new behavior.
5. Run the focused class. Every case must pass.
   The class should hold 26 cases, which is 17 plus the nine of Task 1.
6. Run `just build-partial chat-authorization-server unit`.
   It should report 97 tests, which is 88 plus nine. Report the real count.

## Task 3: Update the operator documents

**Files:** `docs/MCP-CREDENTIAL-ISSUANCE.md`, `shell-scripts/README-chat-build.md`.

1. Check bindings with `drift refs docs/MCP-CREDENTIAL-ISSUANCE.md`.
2. In the credential guide, move a changed JWKS URI from the list of what a recreate
   cannot change to the list of what it can.
3. State the printed line, so an operator can confirm the change in the start log.
4. Keep every other drift case as a referral to the repository owner.
5. Keep the removed agent limit. `CHAT-vepxpmlk` still holds that gap.
6. Print no SQL command. `CHAT-tkabpfxm` stops a direct SQL stand-in for an app call.
7. Carry the same change into `shell-scripts/README-chat-build.md` in short form.
8. Run the shell fence check over both documents.
9. Verify with `drift check`.

## Verification

1. The focused `AgentClientsTests` run reports zero failures.
2. `just build-partial chat-authorization-server unit` reports zero failures.
3. `./shell-scripts/test-flags.sh` still reports all 69 cases match.
4. `shell-scripts/test-authserv-profile.sh` still passes.
5. `bash docs/superpowers/proofs/2026-10-30-agent-mapping-precedence/precedence.sh` still reports 0 failures for seven cases.
6. Every `sh` block of both documents passes `bash -n`.
7. `git diff master...HEAD --check` reports nothing.
8. `drift check` exits 0.
9. A partial GitHub build runs on the branch, because a local Maven run is author-reported.

## Out of scope

This plan does not withdraw an agent that leaves the mapping.
`CHAT-vepxpmlk` holds the missing app function for an update and a removal.
This plan closes the update need in the reconciliation, and it does not close the removal need.

It does not change `requireRowFree`, the replay store, the token lifetime, or the scope rule.
It does not change core or rest.
It starts no container and registers no live agent.
`CHAT-vnljyxpl` keeps the live two-agent check.
