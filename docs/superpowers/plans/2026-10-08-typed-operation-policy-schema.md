# Typed Operation Policy Schema Implementation Plan

> **For inline implementation:** Follow this plan task by task. Do not use subagents. Keep each test change before its production change.

**Goal:** Make initial grant configuration typed, reject unsupported grant values, and preserve the decided wildcard and expiry boundaries.

**Architecture:** `RoleDefinition` validates configuration names and role values during binding. `InitalRoles` keeps only the role list. Existing grant writers remain unchanged. Tests cover binding, seed behavior, and the two stale comments.

**Tech Stack:** Kotlin, Spring Boot constructor binding, JUnit 5, AssertJ, Reactor, Maven.

---

### Task 1: Add failing schema tests

**Files:**
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/init/UserInitConfigBindingTests.kt`
- Verify: `chat-core/src/test/kotlin/com/demo/chat/test/knownkey/RootKeysTests.kt`

- [x] **Step 1: Test the shipped field set.**

Add assertions that `app.init.initialRoles` declares only `roles`. Assert that it does not declare `rolesAllowed` or `wildcard`.

- [x] **Step 2: Test rejected role values.**

Add tests that constructing `RoleDefinition("User", "Message", "-")`, `RoleDefinition("ACTIVE", "Message", "GET")`, and `RoleDefinition("user", "Message", "GET")` raises `IllegalArgumentException`.

- [x] **Step 3: Test accepted configuration names.**

Bind the shipped file. Assert that every role uses an exact `ChatDomain` name, `Admin`, or `Anon`.

- [x] **Step 4: Run the tests and verify the expected failures.**

Run:

```bash
mvn -o -B -pl chat-deploy -am test -Dtest=UserInitConfigBindingTests,RootKeysTests -Dsurefire.failIfNoSpecifiedTests=false
```

Expected result: the new field and role-value assertions fail against the current binding.

### Task 2: Remove the dead binding fields

**Files:**
- Modify: `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/init/UserInitializationProperties.kt:19-23`
- Modify: `shared-deploy-configuration/src/main/config/userinit.yml:15-18`
- Modify: `chat-deploy/src/test/resources/userinit.yml:7-10`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialGrantSeedTests.kt:29-41`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/MockInitializationTests.kt:72-76,136-149`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialUsersFixture.kt:70-76`

- [x] **Step 1: Reduce `InitalRoles` to `roles`.**

Remove the `rolesAllowed` and `wildcard` constructor properties.

- [x] **Step 2: Remove both YAML keys.**

Delete `rolesAllowed` and `wildcard` from the shared configuration and the test configuration.

- [x] **Step 3: Update Kotlin fixtures.**

Change every `InitalRoles` constructor call to pass only the role array.

- [x] **Step 4: Run the binding and initialization tests.**

Run:

```bash
mvn -o -B -pl chat-deploy -am test -Dtest=UserInitConfigBindingTests,LoadInitializationPropertyTests,InitialGrantSeedTests -Dsurefire.failIfNoSpecifiedTests=false
```

Expected result: the field-removal tests pass, and role validation tests remain red.

### Task 3: Validate role names and denial values during binding

**Files:**
- Modify: `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/init/UserInitializationProperties.kt:38-42`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/init/UserInitConfigBindingTests.kt`

- [x] **Step 1: Add the accepted-name set.**

Use `ChatDomain.entries.map { it.wireName }` and `ChatIdentity.entries.map { it.wireName }`. Keep matching case-sensitive.

- [x] **Step 2: Reject unsupported values in `RoleDefinition`.**

Reject `-`, `ACTIVE`, `ROOT`, lower-case aliases, and any name outside the accepted set. Keep wildcard seed rows valid.

- [x] **Step 3: Run the role validation tests.**

Run:

```bash
mvn -o -B -pl chat-deploy -am test -Dtest=UserInitConfigBindingTests,LoadInitializationPropertyTests -Dsurefire.failIfNoSpecifiedTests=false
```

Expected result: all binding tests pass.

### Task 4: Prove seed expiry and wildcard source boundaries

**Files:**
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialGrantSeedTests.kt`
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/RoomOwnerGrantTests.kt`
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/MembershipGrantTests.kt`

- [x] **Step 1: Prove seed rows never expire.**

Assert that a configured seed row stores `expires == 0L`.

- [x] **Step 2: Prove generated Admin rows never expire.**

Assert that each generated Admin wildcard row stores `expires == 0L`.

- [x] **Step 3: Prove the room owner row is the only runtime owner writer.**

Keep the existing authenticated and anonymous owner tests. Add an assertion that the authenticated row has permission `*` and expiry `0L`.

- [x] **Step 4: Prove membership writes no wildcard row.**

Keep the existing permission-set assertion. Add an explicit assertion that no membership row has permission `*`.

- [x] **Step 5: Run the authorization tests.**

Run:

```bash
mvn -o -B -pl chat-security,chat-deploy -am test -Dtest=RoomOwnerGrantTests,MembershipGrantTests,InitialGrantSeedTests -Dsurefire.failIfNoSpecifiedTests=false
```

Expected result: all source-boundary tests pass.

### Task 5: Correct stale comments and schema documentation

**Files:**
- Modify: `shared-deploy-configuration/src/main/config/userinit.yml:1`
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/AuthSummarizerTests.kt:247-248`
- Modify: `docs/superpowers/specs/2026-09-23-operation-policy-draft.md:199-228,240-260,842-861`

- [x] **Step 1: Correct the YAML comment.**

State that configuration names exact domain names, `Admin`, and `Anon`. State that the `*` value marks ownership.

- [x] **Step 2: Correct the test comment.**

State that the production actor set includes the `User` root since `CHAT-mahevldm`.

- [x] **Step 3: Align the draft with the schema decision.**

State that `-` is rejected during configuration binding. Keep the measured legacy evaluator behavior as historical evidence. State that initial seed rows have no expiry field. State that `NONE` and `NOW` belong only to the action model.

- [x] **Step 4: Remove stale schema claims.**

Remove the claim that `rolesAllowed` and `wildcard` are active schema fields. Remove configuration use of `ACTIVE` and `ROOT`. Keep those names only as runtime action-model sources.

- [x] **Step 5: Run documentation checks.**

Run:

```bash
drift check
git diff --check
```

Expected result: both commands pass.

### Task 6: Run the full focused verification

**Files:**
- Verify: all files changed in Tasks 1 to 5

- [x] **Step 1: Run the focused reactor gate.**

Run:

```bash
mvn -o -B -pl chat-security,chat-deploy,chat-service-composite -am test -Dtest=AuthSummarizerTests,RoomOwnerGrantTests,MembershipGrantTests,TopicServiceMemberGrantTests,UserInitConfigBindingTests,LoadInitializationPropertyTests,InitialGrantSeedTests -Dsurefire.failIfNoSpecifiedTests=false
```

Expected result: exit code 0 with zero failures and zero errors.

- [x] **Step 2: Inspect the final worktree.**

Run:

```bash
git status --short --branch
git diff --check
```

Preserve the pre-existing untracked command-bus documents.

- [x] **Step 3: Report the evidence.**

Record the test count, exit code, changed paths, and any unmeasured deployment behavior in the final issue comment.
