# Forward Register Archive — 2026-10

Sections of `forward-register.md` for 2026-10-01 to 2026-10-15, kept
verbatim. The sections to 2026-10-07 moved here on 2026-10-15, when the live
file gained a 64 KB limit. The sections from 2026-10-08 to 2026-10-15 moved
here on 2026-10-16, under `CHAT-jgegpcoi`. Nothing imports this file.

## The authorization grant policy (2026-10-01)

`CHAT-zhjltbky`. Spec:
`docs/superpowers/specs/2026-10-01-authorization-grant-policy-design.md`. Plan:
`docs/superpowers/plans/2026-10-01-authorization-grant-policy.md`.

**This work is merged.** PR #152 merged on 2026-10-01 as
`87657e0c6e8c61c2b2b5e22b60717873ca8ffbd4`. The merge has two parents, so it is
a real merge commit. The merge tree is byte-identical to the branch tip
`19b55c7b`, because `git diff 19b55c7b 87657e0c` was empty. The branch was cut
from `09d4c9a6`, the merge commit of `CHAT-rfzsnbco`.

**This paragraph read "not merged" until 2026-10-02.** The refresh after the
enforcement merge corrected its own section and left this one alone.

### The six owner decisions

1. **One writer holds the room owner row.** The server writes it, at room
   creation.
2. **The port is optional.** A composition with no authorization still creates
   a room.
3. **The shell writes no grant.** A shell that cannot present an identity owns
   no room.
4. **A failed grant write fails the request, and the room stays.** No step of
   the chain compensates another.
5. **An owner may delete a room, open or closed.** The rank carries it.
6. **Wildcard uniqueness is deferred.** `CHAT-esengqpv` holds it.
   **Read that as of 2026-10-01.** `CHAT-esengqpv` adds the guard on
   2026-10-04. See the last section of this file.

### The delete decision, and the rank levels

`TopicServiceAccess.deleteRoom` checks the room key with `REM`. **`ALL` is a
literal**, so the shipped `{User, MessageTopic, ALL}` row does not cover
`REM`. An anonymous caller and a non-owner both deny.

**An owner allows, and a close does not reach that.** Both rows are
wildcards, so level 1 of the rank ties. The owner row names the user key,
which is an `ENTITY`. The close names a domain root, which is a
`DOMAIN_ROOT`. **Level 2 places `ENTITY` above `DOMAIN_ROOT`.** A closed room
still yields to its owner.

Five rows are pinned in `AnonymousAuthorizationMatrixTests` and in
`CassandraAuthorizationMatrixTests`. Both were measured on 2026-10-01, the
Cassandra one against Docker Engine 29.7.2.

### The port, and its condition

`RoomOwnerGrant<T>` sits in `chat-core`, so the composite never reads a
security context. `ContextRoomOwnerGrant` sits in `chat-security`, and it is
the only implementation. `RoomOwnerGrantConfiguration` registers it under
`app.service.composite.auth`, which is the condition of the
`authorizationService` bean it needs.

The composite takes the port as an `ObjectProvider`, which is the pattern that
configuration already uses for `vectorIndexers`. So a composition without
`chat-security` still builds a topic service.

### The failure contract

A failed grant write raises `RoomOwnerGrantException`. It carries the room key
as a field and the cause, and its message names the room id. `ChatException`
gained an optional cause for this.

**The residual is an ownerless room.** The store row, the index row and the
open topic all stay. An operator reads the key from the message and writes the
missing row by hand. No step of the chain compensates another.

**The key type is open**, `Key<*>`. Kotlin refuses a type parameter on a
subclass of `Throwable` at the declaration.

### Two facts that cost a measurement

1. **A raw id cannot bind to `hasAccessTo(Key, String)`.** SpEL resolves a
   method by name, by argument count, and then by assignability. A `Long` is
   not a `Key`, so the call fails with `EL1004E`. Both send expressions were
   in that state. They are repaired by `hasAccessToId`. The remaining sites
   are latent and filed as `CHAT-eoqkbqve`.
2. **A Kotlin data class property has no same-named accessor.** A property
   `dest` compiles to `getDest()`. SpEL resolves `#req.dest` and not
   `#req.dest()`.

`SendCheckExpressionTests` reads the real annotation text and evaluates it
through SpEL. A mutation of either expression made all three of its tests
error with the measured `EL1004E` message.

### The two new issues

Both are children of `CHAT-znprrzhn`, and no launch script, yml or test enables
a check yet, so every row above is latent.

| Issue | What |
|-------|------|
| `CHAT-eoqkbqve` | Repair the access expressions that cannot be evaluated. This must land before any check is wired, because a check that cannot run refuses every caller. |
| `CHAT-esengqpv` | Refuse a second wildcard row for one target. The guard belongs where the row is written. |

**The `Checkout` row of this file read `8fa9c962` when this section was
written.** That lag is deliberate and this section did not move it. The row
read `38ecb01d` on 2026-10-02. See the rule under `Where things stand`.

## Composite access enforcement (2026-10-01)

`CHAT-znprrzhn`. Let this section be the record. The spec and the plan are
`docs/superpowers/specs/2026-10-01-composite-access-enforcement-design.md` and
`docs/superpowers/plans/2026-10-01-composite-access-enforcement.md`.

**This work is merged.** PR #156 merged on 2026-10-02 as
`c069c978365d50da48b4d769f37e94af32fd9793`. The merge has two parents, so it is
a real merge commit. The merge tree is byte-identical to the branch tip
`6970a89a`, because `git diff 6970a89a c069c978` was empty. The branch was cut
from `ade87988`, the merge commit of PR #155.

### What exists

Six composite controllers declare their annotated access interface as a
supertype. **Fourteen operations are judged, on RSocket and on REST.**
`TopicServiceAccess` carries eight, `MessageServiceAccess` three and
`UserServiceAccess` three. Seven of the fourteen are rows of
`docs/ANONYMOUS-AUTHORIZATION.md`.

`RSocketSecurity.anonymous` gives the shell an identity, so `addRoom` and
`listRooms` allow there. That is why the shell creates a room and sends no
credential.

### The owner grant policy of 2026-10-01

Four decisions, all taken by the owner during this work.

1. **`ALL` is renamed `GET_ALL`.** The old name read as "every permission". A
   `*` row is what carries every permission.
2. **Every user may add a room.** A room is unbounded and no counter bounds it.
   The `{User, MessageTopic, NEW}` row reaches every caller that holds an
   identity.
3. **Admin holds `*` on every domain root.** `InitialUsersService` writes one
   row per loaded domain, so a domain a later release adds takes its row with
   no second edit. `AdminRootGrantWiringTests` pins it.
4. **No regular user creates a user.** That is the work of an Admin.

**Rule 3 moves no matrix row.** The actor set of a query is the `Anon` key,
the `User` root and the caller. The `Admin` key is in no other caller's actor
set, so an Admin row reaches the Admin identity alone.

### Three defects found on the way, each measured

1. **A JDK dynamic proxy hides a class level `@RequestMapping`.** Method
   security proxies a controller that carries an annotated supertype. The
   controller implements interfaces, so the proxy is a JDK proxy, and a JDK
   proxy carries the interfaces and not the implementation class.
   `RequestMappingHandlerMapping` then saw no route, and **every REST route
   answered 404**. The routing annotations sit on
   `ChatTopicServiceRestMapping` now. `LongTopicRestTests` passes on the same
   controller, because no method security is active in its slice. The old 404
   was never a mapping defect.
   **Read that 404 as a slice reading only.** A real launch makes a CGLIB
   proxy, and its routes answered. The skipped facade check was the defect
   there. `CHAT-pjymtozd` measured it on 2026-10-04. See the last section of
   this file.
2. **A facade method crosses no proxy.** A default method that calls its
   member on the same object never leaves that object. The member's
   `@PreAuthorize` did not run, and **a denied caller reached the service**.
   Measured under a JDK proxy and under CGLIB. The five facade methods carry
   their own check now.
3. **`AuthorizationDeniedException` had no renderer**, so a REST denial
   answered 500. A 500 is not a refusal to any caller. `KeyRefusalAdvice`
   gained an `AccessDeniedException` handler, which answers 403.

Two refusal shapes are now pinned. RSocket reports
`ApplicationErrorException` with `Access Denied`, because no handler claims
the exception and the transport reports application error 0x201. REST answers
403.

### The build

- Default gate: exit 0, 29 modules, 1507 tests, 0 failures, 0 errors,
  35 skipped. It reports that reality matches `docs/BUILD-HEALTH.md`.
- CI gate: exit 0, 29 modules, 1806 tests, 2 failures, 5 errors, 59 skipped.
  `chat-shell` is the only failing module.

**An exit code of 0 on the CI gate does not mean the reactor is green.** It
means the failure set matches `docs/BUILD-HEALTH.md`.

### The shell failure, and the two issues that hold it

`chat-shell` reports 7 failures, and every one reads `Access Denied`. The plan
states that the shell suite cannot be green when this issue lands. **The call
path separates the two sets, and the message does not.**

| Set | Count | Issue | First refusal |
|---|---|---|---|
| `getRoomByName` is fail closed | 6 | `CHAT-dgjhljbl` | the name lookup |
| no credential | 1 | `CHAT-wbcbptiq` | `addUser` |

**No credential repairs the first six.** `getRoomByName` carries an explicit
deny expression, because a `ByStringRequest` holds a name and no target key to
check. So `CHAT-wbcbptiq` alone cannot make the shell suite green, and its
acceptance criterion says otherwise. See the note below.

**The single credential failure needs an Admin identity**, because no regular
user creates a user. The Admin wildcard rows of rule 3 supply that answer.

**`chat-shell` is recorded in `KNOWN_FAILING_INTEGRATION`, under B12.** That
entry makes the verifier's reading honest.

**Both CI jobs stay red until those two issues land.** CI runs plain maven,
which never reads `KNOWN_FAILING_INTEGRATION` and never reads
`docs/BUILD-HEALTH.md`. **This is the B11 shape**, and B12 states it.

**That table was measured on 2026-10-01, and the room by name merge changed
it.** `CHAT-dgjhljbl` removed the route check on 2026-10-02, so the six split.
**Four repaired, and two moved to the send check.** See
`The room by name route (2026-10-02)` at the end of this file.

### Two issues that need the owner

- **`CHAT-wbcbptiq` carries an unreachable acceptance criterion.** It reads:
  the shell tests are the acceptance gate, so they pass exactly when this work
  is complete. Six of the seven cannot pass from a credential. Either the
  criterion moves to `CHAT-dgjhljbl`, or the two issues close together. The
  issue's own Scope is unaffected: no grant in `userinit.yml` changes, and no
  access expression changes.
- **The `send` rule needs an answer.** The owner wrote that a topic which has
  not been joined cannot receive messages. `joinRoom` writes no grant, and the
  only writer of a send right is the owner row at room creation. So the
  sentence describes ownership today. A rule that a join writes a send grant
  is a separate decision, and nothing implements it. **`CHAT-mfveaecc` holds
  it now, and the shell suite measures it.**

### Two facts that cost a measurement

1. **A scoped `-pl` run is not evidence in this worktree.** A run of
   `-pl chat-core,chat-deploy-memory` resolved `chat-deploy` and
   `shared-deploy-configuration` from `~/.m2`, and reported failures against
   the old `ALL` row in `userinit.yml`. Only the full reactor is trustworthy.
   That is B10, met a third time.
2. **The `DOCKER_CONFIG` trap is real.** `spring-boot:build-image` fails with
   `'username' must not be null` when a stale Docker Hub login sits in the
   default config. The image is then not rebuilt, and `chat-shell` tests the
   image the machine already holds. An empty `DOCKER_CONFIG` repairs it.

### What this work did not deliver

- **The `core` interfaces stay latent.** `PersistenceAccess`, `IndexAccess`,
  `PubSubAccess`, `TopicInventoryAccess`, `IKeyServiceAccess` and
  `SecretsStoreAccess` have no production implementation. `CHAT-ruapxetl`
  holds the programmatic wrappers.
  **Read that line as of 2026-10-01.** PR #182 removed those interfaces on
  2026-10-04, and the role rule seals the core routes.
- **No deployment turns on the checks.** They are on wherever a composite
  controller mounts, and every deployment mounts one.


## The MCP agent identity (2026-10-01)

`CHAT-werokcbb`. Spec:
`docs/superpowers/specs/2026-10-01-mcp-agent-identity-design.md`. Plan:
`docs/superpowers/plans/2026-10-01-mcp-agent-identity.md`.

**This work is merged.** PR #158 merged on 2026-10-02 as
`7930d953d1a24c50cc42bc1a2313b594c2727288`. The merge has two parents, so it is
a real merge commit. The branch was cut from `c069c978`, the merge commit of
PR #156, and master merged into it once before the merge, as `f937fca5`.

**The owner merged while the integration job was red.** That is the documented
B12 state, and the build job passed.

**PR #159 corrected this section on 2026-10-02.** It replaced a false
"not merged" claim and it separated the local `--ci` verifier mode from the
GitHub integration job.

**The `Checkout` row of this file read `8fa9c962` when this section was
written.** That lag is deliberate and this section did not move it. The row
read `38ecb01d` on 2026-10-02. See the rule under `Where things stand`.

### The gap this work closes

The MCP adapter had no account of its own. `docs/MCP-CREDENTIAL-ISSUANCE.md`
issued its credential to `Admin`, because startup created no other account. So
an adapter token carried every right of the deployment administrator.

`ChatIdentity` was the cause. It is a closed set of `ADMIN` and `ANON`, and
`InitialUsersService` refused any third initial user with
`An initial user names an unknown identity: <name>`.

### The shape the owner chose

**The agent is a plain user, and `ChatIdentity` stays closed.** The identity set
does not open. Instead, a third initial user that names no identity loads as an
ordinary user. That is the whole of the mechanism.

Three production files changed.

| File | What |
|---|---|
| `chat-deploy/.../service/init/InitialUsersService.kt` | `loadIdentities` requires `Admin` and `Anon`, and admits every other initial user as a plain user. A blank password generates a credential and prints it. |
| `chat-deploy/.../config/deploy/init/UserInitializationProperties.kt` | `UserDefinition.password` defaults to blank, so the entry may omit the key. |
| `shared-deploy-configuration/src/main/config/userinit.yml` | Declares the `Agent` account. Handle `Agent`, name `MCP ADAPTER`, image `chatimg://agent.png`, and no `password` key. |

**The grants the agent holds are the floor, and nothing else.** No grant row
names the agent as its principal. `InitialUsersService` writes one `*` row per
domain root for the `Admin` key, and the agent key is not that key. The agent is
in no other caller's actor set, so those rows do not reach it.

**The narrowing is a loss, not a gain.** The agent drops the administrator's `*`
reach. It gains no row.

### The measured refusal

`AnonymousAuthorizationMatrixTests` pins the agent matrix against the
administrator matrix. **The two differ at exactly three operations**, and the
plan expected two.

| Operation | Agent | Admin |
|---|---|---|
| `send` to a room the caller does not own | **deny** | allow |
| `addUser`, `User NEW` | **deny** | allow |
| `deleteRoom`, `MessageTopic REM` | **deny** | allow |

Every other operation reads the same, because the shipped rows name the `User`
root and the `Anon` key, and those reach every caller.

**The `send` row is the plan's omission.** The `Admin` `*` row on the
`MessageTopic` root reaches the room `SEND`, so the administrator allows it and
the agent denies it. The spec's agent-versus-admin table names the same three
denials, so the code follows the spec and the plan's count was wrong.

The mutation proof withdraws the generated `Admin` rows. The difference set then
collapses to empty, so the assertion bites.

### The acceptance run

Measured on 2026-10-01, on a `chat-deploy-memory` exec jar on ports 6892 and
6893, with `app.security.agent.username=Agent`.

- The deployment started, printed one `Generated password for account 'Agent':`
  line, and logged zero agent-resolution failures.
- A topic `mcpagent` was created through the deployment. It answered 201 with
  id `1555400854075346944`.
- The harness exited 0 with `connectError` null. The allowed id answered 200 and
  the unserved id answered `NOT_AVAILABLE`, 404. No call answered
  `AUTHENTICATION_REQUIRED`.
- The control replaced the token with junk text. All three calls then answered
  `AUTHENTICATION_REQUIRED` with 401. So the credential was judged.

`docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md` records the run in full, and
`docs/ANONYMOUS-AUTHORIZATION.md` carries the agent matrix.

### Three traps, each of which cost a cycle

1. **The appendix flag list omitted `spring.application.name`.** The golden
   `core-memory-init` set carries it. A start without it fails with
   `Could not resolve placeholder 'spring.application.name'`. The appendix now
   names it as a fifth change.
2. **The stored process id belongs to the launcher, not the JVM.** `chat-build`
   runs maven, and maven forks the server. So `$!` names the wrapper, and
   killing it leaves the server holding its port. Kill the process that owns the
   port, or kill the process group.
3. **The adapter classpath must be absolute.** The harness runs from
   `chat-mcp/src/test/client`, so a relative `chat-mcp/target/classes` resolves
   below that directory and the JVM reports
   `Could not find or load main class com.demo.chat.mcp.McpAdapterMainKt`.

### One finding about the verifier

**`build-health.sh` compares the failing-module set, and it does not compare the
test counts.** A count that moves passes in silence. Measured on 2026-10-01: the
default run reports 1521 tests, and `docs/BUILD-HEALTH.md` recorded 1507, and
the verifier reported that reality matched the document.

### The gates

Measured on 2026-10-01 at the branch tip.

- Default gate: exit 0. 29 modules ran 1521 tests, with 0 failures, 0 errors
  and 35 skipped. It reports that reality matches `docs/BUILD-HEALTH.md`.
- The `--ci` verifier mode: exit 0. 29 modules ran 1820 tests, with 2 failures,
  5 errors and 59 skipped. `chat-shell` is the only failing module, and B12
  records it. **All 7 failures read `Access Denied`,** so this work added none
  of them.
- The `--ci` run rebuilt the test image. The deploy integration module wrote
  its jar inside the run, at 19:26:29, and the gate finished at 19:27:17.
- `drift check` reports `ok`, and `git diff --check` exits 0.

**A green `--ci` verifier mode does not mean green CI.** The verifier reads
`KNOWN_FAILING_INTEGRATION`, so it tolerates `chat-shell`. The GitHub
integration job runs plain maven, which reads neither that list nor
`docs/BUILD-HEALTH.md`. **So PR #158 was red on the integration job.**

Measured on the PR #158 run: `chat-shell` reports 57 tests with 2 failures,
5 errors and 22 skipped. Every failure reads `Access Denied`, and the set
matches B12 exactly. `CHAT-dgjhljbl` holds six and `CHAT-wbcbptiq` holds one.
**No unexpected module fails, and the build job passes.**

### Open after this work

- **`CHAT-okpgpxkj`**, audience validation. It is not enforced.
- **`CHAT-uxgdzpag`**, the remaining Lucene index loads.
- **The `client-init` profile registers no `chat.mcp` client.** Not filed. The
  fix is a scope list in one file.
- **No deployment sets a per-agent grant.** A second adapter is a separate
  decision.

### Cleanup, on 2026-10-02

Three steps of the post-merge protocol ran.

1. Local `master` moved to `c069c978` through `git fetch origin master:master`,
   and it now matches `origin/master`.
2. The issue closed. `CHAT-znprrzhn` is `done`, and the comment names the state
   of every child.
3. The main checkout is **on `chat-znprrzhn-composite-enforcement` at
   `91af1c96`, and not on `master`.** A reader who expects master there is
   wrong. That branch is not this work.

**Read step 3 as of 2026-10-02, the day it was written.** The checkout is on
`master` at `ae227b09` now, and `chat-znprrzhn-composite-enforcement` is a
plain local branch. The 2026-10-02 refresh measured that.

One step did not run, and it needs an operator.

**The worktree is not removed.** It is locked by the live session
(`lock reason: claude session znprrzhn-enforcement (pid 39065)`). That process
is the session that wrote this section, so the lock lifts when the session
exits. Removal then needs `git worktree remove -f -f`. The local branch cannot
be deleted before the worktree is gone, because the worktree holds it checked
out.

**That worktree is gone now.** `git worktree list` on 2026-10-02 returns the
main checkout alone, so the lock lifted and the removal ran.

**The remote branch is deleted.** The owner removed
`origin/chat-znprrzhn-enforcement` on 2026-10-02. `git ls-remote --heads`
returns nothing for that name. An agent could not delete it, because the
classifier refuses `git push origin --delete`.

**One trap worth carrying.** The merge changes
`shared-deploy-configuration/src/main/config/userinit.yml`, and the main
checkout holds an uncommitted edit to that same file. A fast-forward of the
main checkout would refuse or clobber it. The owner resolves that edit, and no
branch of this work touches it.

## The dead auth manager, and the live credential seam (2026-10-02)

`CHAT-kcccoifk`. **This work is merged.** PR #161 merged on 2026-10-02 as
`ae227b09`. The merge has two parents, so it is a real merge commit. The merge
tree is byte-identical to the branch tip `99483bca`, because
`git diff 99483bca ae227b09` was empty.

Two commits carry it. `00451ff8` adds the credential seam test, and `99483bca`
deletes the class. The branch was cut from `ac7b0843`, the merge commit of
PR #160.

### What the deletion removed

`CoreReactiveAuthenticationManager` is gone from
`chat-security/service`. **It reached no caller.** No composition declared it as
a bean. Its only in-repo mention was a commented import on
`RSocketServerConfiguration`, and that import named a package it never lived in.
One commit wrote it, `9009d8ff` ("adds some test. (1/3) to MethodSecurity"), and
no test ever covered it.

**It was worse than dead, and that is why deletion beat repair.** Five measured
differences separated it from the live path.

1. An `onErrorMap` turned a wrong password into
   `InternalAuthenticationServiceException`.
2. It built `ChatUserDetails(user, listOf())`, which dropped the `ROLE_USER`
   authority.
3. It re-read the user from `PersistenceStore` after the key was already
   resolved.
4. `authen.credentials.toString()` turned absent credentials into the text
   `null`.
5. It never set `ChatUserDetails.passwd`, so the password accessor threw.

### The live seam, measured

**No composition declares a `ReactiveAuthenticationManager` bean.** Spring
Security therefore builds
`UserDetailsRepositoryReactiveAuthenticationManager` over the
`ReactiveUserDetailsService` bean. That bean is `CoreUserDetailsService`, and it
reads the credential from the secrets store.

`RSocketCredentialSeamTests` measures that path against a running composition
root. It boots `ChatApp` with the memory selectors, connects a real
`RSocketRequester` over TCP, and presents a real `UsernamePasswordMetadata`. It
mocks nothing.

Two tests pin the seam, and each names the change that would break it.

1. A wrong credential is refused at the setup frame.
2. A right credential supplies the identity that owns the room it creates.

The second reads the removal of that room. `CompositeAccessEnforcementTests`
pins the control: a caller with no credential creates a room and is then refused
that same removal. So the removal turns on the identity the credential supplies,
and on nothing else.

**The mutation proof.** Removing `.simpleAuthentication(Customizer.withDefaults())`
fails both tests. The first then reads `onNext(<id>)`, because the wrong
credential is ignored in silence. The second reads
`ApplicationErrorException (0x201): Access Denied`, because no identity arrives.

### The fact that cost the most

**A refused RSocket setup does not fail the connect call.** `connectTcp`
completes with a requester, and the `RejectedSetupException` lands on the first
request that uses it. A client that awaits only its connect call reads a refused
setup as a success. The test asserts the error on the first request for this
reason.

**The refusal text is a coincidence.** The message reads `Invalid Credentials`,
which is also the text of the deleted class's exception. `javap` on
`AbstractUserDetailsReactiveAuthenticationManager` shows that Spring Security
itself supplies that string, so the match proves nothing about the deleted
class.

### One false lead, recorded so nobody repeats it

`grep -rn "Invalid Credentials"` returned the deleted class and `Exception.kt`.
That reading could suggest the class was live. The class was already gone, and
the framework owns the string. **A shared message text is not shared code.**

### The build

- Default gate: 1523 tests, 0 failures, 0 errors, 35 skipped. The count moved
  from 1521, and `CHAT-kcccoifk` added the two seam tests.
- CI run `36982153312` at head `99483bca`: the `build` job passes, and the
  `integration` job fails. `chat-shell` is the only failing module, with 57
  tests and 2 failures, 5 errors and 22 skipped. That set matches B12 exactly,
  so this work added no failure. `CHAT-dgjhljbl` holds six and `CHAT-wbcbptiq`
  holds one.

### One item carried into `CHAT-wbcbptiq`, and the measurement refuted it

**This item was wrong, and `CHAT-wbcbptiq` corrected it on 2026-10-02.** It read:
`LoginCommands.login` resolves the user through the user store, and not through
the credential. So a login stores metadata for a user the secrets store may not
hold, and the refusal above then arrives on the first request rather than at the
login.

**Measurement refutes it.** `MetadataRSocketRequester` reads its metadata
provider on every route call. `ShellStateConfiguration` builds that answer from
the stored login. So the `user-by-handle` call carries the credential, and the
RSocket server judges it before the handler runs. **A wrong password fails the
login call.** `LongLoginCommandsTests` pins that refusal.

**The reading came from source and not from a run.** `LoginCommands.login`
carries no explicit credential check, so reading it alone suggests the store
holds the whole decision. The metadata travels outside that method. See the
shell Admin identity section at the end of this file.

### Open after this work

Nothing new. The list at the end of the MCP agent identity section stands:
`CHAT-okpgpxkj` audience validation, `CHAT-uxgdzpag` the remaining Lucene
loads, the `client-init` scope gap, and the per-agent grant decision.

**The `Checkout` row of this file read `ae227b09` at that refresh.** That was
the last substantive merge before it, so the row followed the rule under
`Where things stand`. **That refresh has been superseded**, and the row reads
`38ecb01d` now. See the last section of this file.

## The room by name route (2026-10-02)

`CHAT-dgjhljbl`. **This work is merged.** PR #163 merged on 2026-10-02 as
`c3aa6546`. The merge has two parents, so it is a real merge commit. **This
refresh is `CHAT-zuzboatu`**, and it carried the measured shell reading. PR
#164 merged on 2026-10-02 as `2969050f`.

### The owner reversed a decision

**The owner decided on 2026-10-02 that `topic-by-name` answers every caller
that matches a room.** That replaced the 2026-10-01 decision, which made the
route fail closed.

`getRoomByName` takes `ByStringRequest`, whose only field is a name. **So no
target key exists at the check, and no honest expression can judge the route.**
The route carried the literal `false`, and it refused every caller.

**The answer stays minimal.** `MessageTopic` carries the room key and the full
room name, and nothing else. So the route leaks no room content. **The access
condition travels with the next operation**, and every operation on a resolved
room holds its own check.

**An unknown name still answers `NotFoundException`.** A miss is not a grant
question.

**The owner named an after-fetch authorization and deferred it.** It would
filter after the fetch, for a target whose key was unknown at the fetch. The
owner ranks that work below the first production release. **No issue tracks
it.**

### Four documents moved

- The composite access enforcement spec states the new decision, and it keeps
  the old text, so a reader sees what changed.
- The plan marks Task 5 as superseded, and says not to run its steps. The
  steps stay as the record of that session.
- `docs/ANONYMOUS-AUTHORIZATION.md` moves the matrix row, the grant table row
  and three prose passages.
- `docs/BUILD-HEALTH.md` moves the shell failures out of the `getRoomByName`
  set.

### Two tests, and a mutation proof

`RSocketBoundaryProbeDeniedTests` sends one call through a broker that denies
everything. The delegate answers the room, and the test reads the key and the
name back from the wire. **A refusal there would mean a check still guarded
the route.**

`SendCheckExpressionTests` replaces its deny test with one that pins the
absence of an expression, beside the seven expressions it still evaluates.
**Restoring the literal `false` fails it at `SendCheckExpressionTests.kt:214`.**

### The measured shell reading

`build-health.sh --ci` ran on 2026-10-02 at `6a62c227`, and it rebuilt the
shell test image. It reports 1822 tests, 2 failures, 1 error and 59 skipped,
and `chat-shell` is still the only failing module. **Seven failures became
three.**

| Set | Before | Now | The refusal now |
|---|---|---|---|
| `LongPubSubCommandsTests` | 4 | 2 | `send`, on the room the caller created |
| `LongShellTopicCommandsTests` | 2 | 0 | repaired |
| `LongUserCommandsTests` | 1 | 1 | `addUser`, which is the work of an Admin |

**The two remaining send failures are the evidence.** Each one failed inside
`getRoomByName` before, and each fails at `PubSubCommands.kt:54` now, which is
the send call. **So the room read succeeded and the send check refused it.**
That is the access condition arriving on the operation beside the route.

### One trap, and it is the verifier's

**`build-health.sh` compares the failing-module set, and it does not compare
test counts.** The module failed before and it failed after, so the run
reported that reality matched `docs/BUILD-HEALTH.md` while seven failures
became three. **A count that moves passes in silence.** The register recorded
this once before, on 2026-10-01.

### One finding filed

**`CHAT-mfveaecc`.** The shell cannot send to the room it creates. Two
candidate causes exist and **neither is measured**, so do not quote one as the
cause. The first is that the room holds no owner row for the shell caller. The
second is that the row exists and the check reads another key. **A deployment
run answers it.**

### One reading the owner still holds

**`CHAT-wbcbptiq` carried an unreachable acceptance criterion.** It read that
the shell tests are its acceptance gate. Six of the seven were never
repairable by a credential, and four of those six are repaired now. The
criterion reached one failure, which is the `addUser` refusal. **The owner
decided that it moves, and `CHAT-wbcbptiq` now owns `addUser` alone.**

### Open after this work

- `CHAT-mfveaecc`, the send refusal, filed on 2026-10-02. **It holds the only
  remaining shell failure.** **Merged since: PR #169, `6fa2ba60`, on
  2026-10-03.**
- The after-fetch authorization. Named by the owner, and unfiled.

**The `Checkout` row of this file read `38ecb01d` at that refresh.** That was
the last substantive merge before it, so the row followed the rule under
`Where things stand`. The row reads `d25ca495` now.

## The shell Admin identity (2026-10-02)

`CHAT-wbcbptiq`. **This work is merged.** PR #166 merged on 2026-10-02 as
`38ecb01d`. The merge has two parents, so it is a real merge commit. The merge
tree is byte-identical to the branch tip `55576043`, because both trees are
`cbbd1390`. **This refresh is `CHAT-kmvavolb`.**

### The carried claim was wrong, and it was the whole first question

The register recorded that `LoginCommands.login` resolves the user through the
user store and not through the credential. **Measurement refuted it.**

`MetadataRSocketRequester` reads its metadata provider on every route call, and
`ShellStateConfiguration` builds that answer from the stored login. So the
`user-by-handle` call carries a `UsernamePasswordMetadata`, and the RSocket
server judges it before the handler runs. **A wrong password fails the login
call.**

**So no production seam was added.** The login already presented the credential.
`LongLoginCommandsTests` pins the refusal, and `ShellRequesterTests` pins the
provider that carries it.

**The reading came from source.** The method holds no explicit credential check,
so the store reads as the whole decision. The credential travels outside that
method, in the requester.

### The one production change

`ShellStateConfiguration.clearLogin()` owns the no-login transition. It clears
the credential and the identity in one step, because the two are one state.
`LoginCommands.login` calls it in the catch branch. A caller that never logged
in keeps the Anon floor.

**The shell login lives in a companion object, so it outlives one test class.**
`ShellIntegrationTestBase` clears it before every test. Without that, a login in
one class would decide a test in another class, and the suite would pass or fail
by order.

### The tests

`ShellDeploymentAccount` reads the shipped Admin password from
`config/userinit.yml`, which is the same artifact the deployment image loads.
**No credential sits in test source**, and a blank password is refused with a
message that names the key.

- `LongLoginCommandsTests` holds the Admin login, the `whoami` read, and the
  wrong-password refusal. Six tests.
- `LongUserCommandsTests` logs in as Admin before `addUser`. Nine tests.
- Its companion control, `an anonymous caller cannot create a user`, asserts
  that the same call still reads `Access Denied` with no credential.
- `ShellRequesterTests` pins the metadata provider. Three tests.

**The control is what makes the Admin test mean anything.** A grant that widened
would satisfy the Admin test alone. Removing the Admin login makes that test
fail with `Access Denied`, and removing `clearLogin()` fails two login tests.

### The build reading

`build-health.sh --ci` exits 0 and reports no drift, at master `96c88e96`.
29 modules ran 1829 tests, with 2 failures, 0 errors and 62 skipped.

**`chat-shell` reports 64 tests, 2 failures, 0 errors and 25 skipped.** Three
failures became two, and the two are the `send` refusals that `CHAT-mfveaecc`
owns. `LongUserCommandsTests` is green.

The skip count moved from 22 to 25, because this work added one user test and
two login tests to the disabled base classes. `docs/BUILD-HEALTH.md` records
both counts.

### The trap that made the first gate invalid

**The first `--ci` run had no `DOCKER_CONFIG` override, and it was not a
reading.** `build-image` failed with `'username' must not be null`, because a
stale Docker Hub login sits in the default config.

That run reported `chat-deploy-memory-integration-tests` as a **new unrecorded
failing module** and exited 1 on drift that no source change caused. **The image
was then not rebuilt, and `chat-shell` tested whatever image the machine already
held.**

**Check the image, and not the test result.** The buildpack image timestamps are
sentinels, so a date proves nothing. The image id moved from `sha256:ca616a99`
to `sha256:92c24224` on the valid run, and that change is the proof.

### The CI reading

Run `37057740837` at `55576043`. The `build` job passes. The `integration` job
fails, and the red set matches B12 exactly.

**One module fails, and it is `chat-shell`.** `chat-authorization-server`,
`chat-crypto`, `chat-presence` and `chat-deploy-e2ee` are SKIPPED, because the
workflow runs no `-fae` and maven stops after the first failure.

`chat-shell` reports 64 tests, 2 failures, 0 errors and 25 skipped.

**The CI result is the stronger evidence, because CI builds its own image.**
`LongLoginCommandsTests` runs 6 with 0 failures there, so the Admin login is
accepted by a deployment image that CI built. `LongUserCommandsTests` runs 9
with 0. `LongShellTopicCommandsTests` runs 4 with 0, and `ShellRequesterTests`
runs 3 with 0.

### What this work did not deliver

- **No grant in `userinit.yml` changed, and no access expression changed.** The
  shell login carries the identity that the shipped Admin rows already answer.
- **The two send refusals stay.** `CHAT-mfveaecc` holds them, and no credential
  repairs them.
- **No deployment sets a shell credential.** The credential is a test and
  interactive-shell input.

### Open after this work

- `CHAT-mfveaecc`, the send refusal. **Merged since: PR #169, `6fa2ba60`, on
  2026-10-03.**
- The after-fetch authorization. Named by the owner, and unfiled.

**The `Checkout` row of this file read `38ecb01d` at that refresh.** That was
the last substantive merge before it, so the row followed the rule under
`Where things stand`. The row reads `d25ca495` now.

## The member send grant (2026-10-02)

`CHAT-mfveaecc`. Branch `chat-mfveaecc-join-grant`. **This work is not merged.**
**Merged since: PR #169, `6fa2ba60`, on 2026-10-03.**

### Three owner decisions

1. **A join grants the member `SEND` on the room, and the grant never
   expires.** A leave sets that grant to expire at the time of the leave.
2. **An anonymous caller cannot join a room.**
3. **`leaveRoom` checks `JOIN` on the member, as `joinRoom` does.**

### What exists

- `RoomMemberGrant` in `chat-core` is the port. `MembershipSendGrant` in
  `chat-security` is the only writer. `RoomMemberGrantConfiguration` registers
  it under `app.service.composite.auth`.
  **Renamed under `CHAT-lfaajjcj`.** The writer is `MembershipGrant` now, and
  the port methods are `grantMembership` and `expireMembership`. A join grants
  `SUBSCRIBE` beside `SEND`. See `The member subscribe grant (2026-10-02)`.
- `AuthorizationService.getStoredGrants` reads the stored rows of one principal
  and one target. It does not summarize, so a writer can see expired rows.
- `TopicServiceImpl.joinRoom` refuses the `Anon` member with
  `AnonymousJoinException` before any write.

### Facts that cost a measurement

- **The anonymous join passed the access check.** A probe measured
  `hasAccessToId(Anon, 'JOIN')` as true for an anonymous caller, because a key
  holds every right over itself. The refusal sits in the composite for that
  reason.
- **The writer changes a row in place, and does not append.** Level 3 of the
  rank orders rows by key id, and a uuid key carries no order. One expiry per
  member and room makes the order irrelevant.
- **The old leave check let any caller remove any member.** The shipped
  `{User, MessageTopic, JOIN}` row reaches every caller, and the check named
  the room. A room owner cannot remove another member through this route now.
- **The summarized read answers one row per permission.** A test that asks
  `getAuthorizationsAgainst(..., "SEND")` must filter on `SEND`. The shipped
  `GET` and `NEW` rows on the `MessageTopic` root are in the answer too.

### The build

- `--ci`: 1848 tests, 0 failures, 0 errors, 63 skipped. `chat-shell` reads 66
  with 26 skipped. **B12 is resolved, and `KNOWN_FAILING_INTEGRATION` is
  empty.** The image id moved to `sha256:1965fa84`.

### One finding, read from source and not measured

**The core RSocket routes carry no access check.** `pubsub.sendMessage`,
`persist.authmetadata`, `index.authmetadata` and `secrets` are mounted on every
core launch. `RSocketServerConfiguration.kt:59` permits every request, and the
core access interfaces have no production implementation. So a caller with no
credential can send to any room, can name any sender, and can write a grant
row. The composite checks do not reach these routes. The `client` backend uses
them, so closing them needs a service identity decision. `CHAT-rdlghoqe` holds
it.

## The core route rule (2026-10-02)

`CHAT-rdlghoqe`, with four children: `CHAT-ekndrtbd`, `CHAT-iwvnqnju`,
`CHAT-uepzzxmd` and `CHAT-ciuuiwvp`. Branch `chat-rdlghoqe-core-routes`.
**This work is not merged.**
**Merged since: PR #171, `2d64d671`, on 2026-10-03.**

### The measurement that opened it

A caller with no credential read the `Admin` password hash, read every grant,
wrote a `*` grant, and sent a message in the name of `Admin`. Each call went to
a core RSocket route, and each one completed. `RSocketServerConfiguration`
permitted every request, and the core access interfaces have no
implementation.

### The owner decision

**One rule at the seam.** `persist.**`, `index.**`, `pubsub.**`, `secrets.**`,
`key.key` and `key.rem` require `ROLE_SERVICE` or `ROLE_ADMIN`. The composite
routes keep their method checks. The shell commands that call a core route
directly need an `Admin` login.

### Facts that cost a measurement

- **Every user held `ROLE_USER` alone, `Admin` included.** The rule needed
  role mapping first. `CoreUserDetailsService` adds the two roles now.
- **Root keys load after singleton creation.** The `Admin` key is read through
  a supplier at each login. A key read inside the bean method would run before
  the roots exist.
- **`key.rootOf` must stay open.** The shell resolves a room creator through it
  before `addRoom`, and an anonymous caller may add a room. That is a
  narrowing of the `key.**` rule that the owner first chose.
- **The injected `RSocketRequester.Builder` is one mutable object.** A
  `setupMetadata` call in one test reached a later connection in another test.
  Build a fresh requester per connection.
- **`AuthenticationService.setAuthentication` stores the value as given.** A
  test password needs the `{noop}` prefix.
- **The `rest` facade calls composite routes alone.** Only the authorization
  server reads core routes, so it alone sends the `Service` credential.

### The build

- The first `--ci` run read 6 errors, all in `LongUserCommandsTests`, and each
  read `Access Denied`. Those shell commands call core routes with no login.
  The six tests log in as `Admin` now, and a new control asserts that an
  anonymous core call is refused.
- The second `--ci` run: 1859 tests, 0 failures, 0 errors, 64 skipped, and no
  drift. `chat-shell` reads 68 with 27 skipped. The image id moved to
  `sha256:08c7812b`.

### Not measured

- **No authorization server ran against a locked core.** The launch flag, the
  environment fallback and the startup refusal of a blank password are in
  place. A real login through the authorization server has not run.

### Operator change

**Export `CHAT_SERVICE_PASSWORD` before you start a core and the authorization
server.** The authorization server refuses to start with a blank password.
`docs/BUILD.md` carries the steps.

## The member subscribe grant (2026-10-02)

`CHAT-lfaajjcj`. Branch `chat-member-subscribe-grant`. **This work is not
merged.**
**Merged since: PR #173, `0c71c59d`, on 2026-10-03.**

### The gap

`listen` calls `message-listen-topic`, which checks `SUBSCRIBE` on the room.
Only the owner `*` row and the `Admin` `*` row on the `MessageTopic` root
carried it. A join granted `SEND` alone. So a member that joined could send and
could not listen. The owner found it in a manual shell run.

**The refusal was silent.** `PubSubCommands.listen` called `subscribe()` with
no error handler, so the shell dropped `Access Denied` and printed nothing.

### The owner decision

**A join grants `SUBSCRIBE`, and a leave expires it**, as for `SEND`.

### What changed

- `MembershipSendGrant` is `MembershipGrant`. It writes one `SEND` row and one
  `SUBSCRIBE` row per member and room, and it expires both on a leave.
- The port methods are `grantMembership` and `expireMembership`.
- The shell `listen` prints the end of a refused stream and removes the
  listener.

### The evidence

- `MembershipGrantTests` 9, `TopicServiceMemberGrantTests` 6,
  `RoomMemberGrantWiringTests` 2, `StandardUserJoinSendTests` 3. All pass.
- The new standard-user test listens over TCP. A listen before the join is
  refused, a joined listen receives the message that the member sends, and a
  listen after the leave is refused.
- Mutation: removing `SUBSCRIBE` from the writer fails 5 writer tests, and the
  joined listen fails with `Access Denied` at `StandardUserJoinSendTests.kt:156`.
- `build-health.sh --ci`: 1863 tests, 0 failures, 0 errors, 64 skipped, and no
  drift. `chat-shell` reports 68 tests, 27 skipped and 41 executed. The image id
  moved to `sha256:1103cdad`.

## REST bearer relay to core (2026-10-03)

`CHAT-mpjtnpqv`. Branch `chat-mpjtnpqv-token-relay`. The design is approved.
The work is not merged.
**Merged since: PR #176, `4e75769d`, on 2026-10-03.**

The REST resource server validates the bearer token. The REST RSocket client
now attaches request bearer metadata when the context contains an
`AgentAuthenticationToken`.

The shared agent types live in `chat-security` under
`com.demo.chat.config.agent`. The core does not load `chat-webflux`.

The core binds agent properties as optional. No agent values disable bearer
validation. Partial values fail startup and name the missing property.
REST requires the complete set when its resource server starts.

`RSocketAuthenticationManager` sends simple credentials to the password
manager. It sends bearer metadata to the JWT manager. A core without a JWK
uses `BearerAuthenticationNotConfiguredManager`, which refuses bearer
metadata before anonymous authentication.

Core authentication and authorization refusals use a typed
`RSocketSecurityErrorPayload`. `CoreSecurityErrorDecoder` reads its `kind`
field. REST maps authentication refusal to 401 and authorization refusal to
403. The mapping does not inspect exception messages.

`RSocketIdentityPrecedenceTests` covers request metadata over setup metadata,
setup fallback, and refusal without fallback.
`BearerDenialNoDownstreamEffectsTests` covers wrong-client and expired-token
denial with no service call.
`RSocketServiceCredentialPreservedTests` covers the Service credential.
`RestBearerRelayContextTests` covers the no-bearer rule without an agent token.

`RestToCoreBearerDeploymentTests` starts separate core and REST JVM processes.
It sends a valid REST token through a room create and remove flow. The test
passed after the local RSocket config began using `CORE_HOST` and `CORE_PORT`.

Issuer and audience checks remain under `CHAT-okpgpxkj`. That issue depends on
`CHAT-mpjtnpqv` because it extends this core bearer path.

### Review repairs

All composite auth configurations require `app.service.composite.auth=true`.
The RSocket server refuses startup when that property is absent, empty, or false.
The codec and key-verification handler do not depend on the auth property.

`CompositeAuthConditionTests` reproduced activation of auth services with empty and invalid values before the condition repair.
The repaired conditions pass all three cases.

`CoreBearerDenialNoDownstreamEffectsTests` sends expired and wrong-client tokens through the production RSocket server.
Both cases assert zero calls to the controller, persistence, index, secrets, and pubsub.
The same test context checks that production excludes Boot's security registration and holds one security interceptor.
The isolated duplication test confirms that enabling Boot's registration adds another customizer.

The two-process test uses executable jars from the same reactor and runs only when explicitly enabled.
An independent Service connection reads the created room's owner row.
The test compares that principal key with the configured Agent key, including the root and empty marker.
The test passes with the shipped Agent account.

The local `build-health.sh --ci` repair run reports 1894 tests, zero failures, zero errors, and 65 skipped tests.
The shell reports 77 tests, zero failures, zero errors, and 27 skipped tests.
The rebuilt image is `sha256:279913975476175fc1a83865d705e7c514a6853b4cd595084286998cbefe05e6`.
This run rebuilt the image before the shell tests.
The separate opt-in deployment test also passes.
All 19 launch-flag cases pass.

### Final review decisions

The startup guard now checks the literal `app.service.composite.auth=true` value before accepting any security interceptor.
An interceptor supplied by another configuration cannot bypass that property check.

The `rootkeys` actuator endpoint requires actuator credentials again.
The REST startup client already sends Basic credentials through `HttpRootKeyConsumeOnStart`.
Anonymous access is unnecessary and is rejected.
The current startup client uses the existing `actuator:actuator` defaults.
Configurable startup credentials remain outside this repair.
`CHAT-npqgshiu`, under `CHAT-dmnhxnsp`, now tracks that existing limitation.
It requires a two-process test with non-default actuator credentials and does not block this bearer-relay repair.

The three Redis test applications now exclude Boot's RSocket security auto-configuration, as production does.
The `rest-core-e2e` profile now enables the two-process tests through Surefire.
Run the opt-in test from the repository root:

```bash
mvn -B -pl chat-deploy-memory-integration-test -am verify \
  -Prest-core-e2e -Dtest=RestToCoreBearerDeploymentTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The earlier 1894-test result predates these final repairs.

The final Redis and shell integration reactor reports 1472 tests, zero failures, zero errors, and 66 skipped tests.
The shell reports 77 tests, zero failures, zero errors, and 27 skipped tests against a rebuilt image.
That image is `sha256:0f708618b9328ee217de18fb2bc3e1f926e4adbc605a31f7e48ec1fe308236a0`.
The separate opt-in run passes both deployment tests without an extra system property.
The full reactor ran again after these final repairs. `build-health.sh --ci` reports 1901 tests in 30 modules.
It reports 0 failures, 0 errors, 66 skipped tests, and no drift.
The shell reports 77 tests and 27 skipped tests.
That run rebuilt the shell image as `sha256:34dded7aba26cd2a5dc711e08784380bdef5f6a6e76c6679acbd73f4e9303e47`.

### The core scope check

The flow and state diagrams in `docs/REST-TOKEN-RELAY.md` were read from the
code. They showed that the core bearer path did not check the required scope.

A test measured the gap first. A token from the agent client with the
`openid` scope alone created a room on `topic.topic-add` at the core. REST
refused the same token with 403.

`RequiredScopeAuthenticationManager` now wraps the core JWT manager. A token
without the required scope gets the AUTHORIZATION envelope. Both paths read
the authority from `AgentSecurityProperties.Agent.requiredAuthority`.

`CoreBearerDenialNoDownstreamEffectsTests` holds the refusal and a control.
In the control, a valid agent token adds a room on the same route.

`build-health.sh --ci` then reported 1903 tests in 30 modules, with 0 failures,
0 errors, 66 skipped, and no drift. The shell reported 77 tests and 27 skipped.
The run rebuilt the shell image as `sha256:81a39e5f`.

**One gap stays open, and it is read from source alone.** A `@PreAuthorize`
denial happens in the handler, after the interceptor chain. So it reaches REST
without the typed envelope. The REST status for that case is not measured.

### Security refusals carry RSocket error codes

The diagrams showed a second gap. The first implementation typed core
refusals with a JSON envelope inside the error message, from a payload
interceptor. A reactive `@PreAuthorize` denial happens after the payload chain
completes, so it bypassed the envelope.

`RestToCoreBearerDeploymentTests` measured it. The agent removed a room that
`Anon` created, and REST answered 500.

**The owner chose RSocket error codes on 2026-10-03.** The core sends each
security refusal as `CustomRSocketException`. Code `0x401` means
authentication, and code `0x403` means authorization. The message keeps its
human text, such as `Access Denied`.

`RSocketSecurityErrorInterceptor` sets the code. It is a responder interceptor,
so it wraps the outermost responder and sees handler denials too. The JSON
envelope and the payload interceptor are removed. The REST decoder reads the
code and never the text. The same two-process test now reads 403.

**The wire type of a handler refusal changed.** It was
`ApplicationErrorException` with code `0x201`. It is `CustomRSocketException`
with code `0x403` now. `CompositeAccessEnforcementTests` and
`CoreRouteAccessTests` pin the code. A setup refusal is unchanged. It is still
`RejectedSetupException` with `Invalid Credentials`.

**The shell shows `Access Denied` again.** The envelope had made the shell show
`The core refused authorization.` A refusal now keeps the message of the core.
`LongUserCommandsTests` asserts the type and the text.

`build-health.sh --ci` reported 1905 tests in 30 modules, with 0 failures, 0
errors, 67 skipped, and no drift. The shell reported 77 tests and 27 skipped.
The run rebuilt the shell image as `sha256:5a07f4a6`.

### The first drift binding

**`drift check` bound no document before this work.** Measured on 2026-10-03:
`drift status` answered `[]`, and `drift check` reported `docs_total: 0`. So
every earlier `drift check ok` in this repository checked nothing. Read such a
claim as empty unless `drift status` lists the document.

`docs/REST-TOKEN-RELAY.md` is the first bound document. `drift.lock` at the
repository root binds it to 16 source files on the relay path.

Two limits of drift v0.7.0, measured in a scratch repository:

- **A Kotlin symbol anchor fails.** `drift link doc 'File.kt#symbol'` reports
  `cannot compute fingerprint`, and the lock does not change. Bind whole files.
- **A file anchor reads the whole file.** Any edit to a bound file marks the
  document STALE, even an edit the document does not describe. Review the
  prose, then refresh with `drift link`, as `AGENTS.md` requires.

A mutation proved the binding. One appended comment line in
`CoreSecurityErrorDecoder.kt` made `drift check` report STALE and exit 1.

## The REST message list, and a core miss as 404 (2026-10-03)

`CHAT-evxtlmfs` and `CHAT-undefoqd`. Branch `chat-undefoqd-rest-list-messages`.
**This work is merged.** PR #178 merged on 2026-10-04 as `04d8424d`, a merge
commit with two parents. Its tree is identical to the branch tip `c2dee33c`.

### A core miss answers 404

`KeyRefusalAdvice.coreNotFound` maps `CoreNotFound` to 404 and keeps the core
message. **Before the change, a core miss through the REST facade answered
500 with no message.** Two readings measured it on 2026-10-03: the
two-process `RestToCoreBearerDeploymentTests` with
`GET /topic/name/no-such-room`, and `RestCoreNotFoundMappingTests` in
`chat-webflux`. Both pin 404 now.

### The REST route for listMessages

`GET /message/list/{id}` answers NDJSON and completes after the last stored
message. It checks `SUBSCRIBE` on the room, as the RSocket route does.

### The message controller had never been enforced over REST

**Under method security, every `/message` route answered 404.**
`ChatMessageServiceController` kept its class level `@RequestMapping`, and a
JDK proxy hides it. `CHAT-znprrzhn` repaired that defect for `/topic` alone.

**Read that 404 as a slice reading only.** It was measured in a `@WebFluxTest`
slice. A real launch on 2026-10-04 answered every route and skipped the
checks. See `The REST message routes in a real launch (2026-10-04)` below.
The facade methods of `ChatMessageServiceRestMapping` also carried no check,
so a fix of the mapping alone would have opened them.

The routing annotations now sit on `ChatMessageServiceRestMapping`. Each of
the four facade methods carries the check of its member: `SUBSCRIBE`,
`SUBSCRIBE`, `GET`, and `SEND`.

**No earlier test could see it.** `LongMessageRestTests` runs without method
security. The two-process facade delegates to the core, which judges the call
there.

### Mutation proof

- Removing the list check: a denied caller reads 200.
- Removing the interface mapping: every `ListMessagesRestTests` case reads
  404.

`MessageRestAccessTests` refuses a denied caller at the three other routes,
and the service never runs. `ListMessagesRestTests` holds the list route.

**The repair is its own commit.** The owner asked for the split on
2026-10-03. The repair commit lands first, because the list route needs it.

### One trap met again

**A hyphenated room name collides in the Lucene name index.** The first
two-process run named a room `rest-list-room`. It shared tokens with
`rest-relay-room`, so the second add failed with `Object already exists`, and
REST answered 500. The test uses `restlistroom` now. `CHAT-hajmhslp` holds
the defect.

### One stale passage, not changed

`docs/ANONYMOUS-AUTHORIZATION.md` still states that an RSocket refusal
answers `ApplicationErrorException` with code `0x201`. Since `CHAT-mpjtnpqv`,
it is `CustomRSocketException` with code `0x403`. This work did not change
that passage.

## The REST message routes in a real launch (2026-10-04)

`CHAT-oykeniec`. Branch `chat-oykeniec-shape-c-record`.

### The question

`CHAT-evxtlmfs` measured a 404 on every `/message` route under method
security. That reading came from a `@WebFluxTest` slice alone. This work ran
the single process launch of `docs/MCP-CREDENTIAL-ISSUANCE.md` on two jars:
`67c66bca`, before the repair, and `04d8424d`, after it.

### The launch

- The `chat-deploy-memory` exec jar, built with `-Pexpose-webflux,deploy`.
- The appendix flags, with `server.proto=rest` and `composite.auth=true`.
- **`--app.primary=REST` as well.** The appendix command does not start
  without it since 2026-10-03. The appendix carries it now.
- One agent token: ES256, `client_id` `shapec-client`, scope `chat.mcp`.
  `jshell` and nimbus 10.4 minted it with a key made for the run. The
  production decoder validated it.

**The token did not come from the authorization server.** PID 16436 held
6790 and 6791, where the client of the authorization server points.

### The result

| Call | `67c66bca` | `04d8424d` |
|---|---|---|
| `POST /message/send/OWNED` | 201 | 201 |
| `GET /message/list/OWNED` | 404, no route | 200, completes |
| `GET /message/topic/OWNED` | 200 | 200 |
| `GET /message/id/{message}` | 200 | 200 |
| `POST /message/send/OTHER` | 500, reached the service | 403 |
| `GET /message/topic/OTHER` | 200 | 403 |
| `GET /message/list/OTHER` | 404, no route | 403 |
| `GET /message/list/{unknown}` | 404 | 404 |
| any route, no token | 401 | 401 |

`OWNED` is a room the agent created. `OTHER` is a registered room id on which
the agent holds no grant.

**The real launch did not answer 404 before the repair.** It answered every
route, and the facade checks did not run. A listen without `SUBSCRIBE`
answered 200. So PR #178 closed an open access path in this launch shape.

### Not verified

**The proxy type of the real context.** A probable cause is that Spring Boot
forces class proxies and a slice does not, so the class level mapping stays
visible. Neither half was checked. `CHAT-pjymtozd` holds the same question
for `/topic`, where `CHAT-znprrzhn` recorded the 404 from a slice too.

**Verified on 2026-10-04 under `CHAT-pjymtozd`.** See the next section.

### Three findings filed

- `CHAT-sdvmkidi`. An in-process `NotFoundException` answers 500 in REST.
  `/persist/topic/add` mints a key and stores no room, so a join on that id
  raised it.
- `CHAT-pjymtozd`. The `/topic` 404 claim needs the same real launch.
- `CHAT-sztbozcm`. Other single process launch commands do not start since
  2026-10-03. The `docs/EMBEDDING-PROVIDERS.md` command fails on
  `spring.application.name`, and next on a missing `TypeUtil` bean. The vector
  gate omits `app.primary`, and it was not run.

### One trap

**The `conda` CLI fails on this machine.** `pydantic-core` 2.46.3 does not
match the `pydantic` installed beside it, which requires 2.46.4. So a step
that needs miniforge Python cannot run until the environment is repaired.

**The last sentence above is wrong.** Measured on 2026-10-04: `conda activate
base` exits 0, and the vector gate ran under miniforge Python. Only a
subcommand that loads the conda plugins fails, such as `conda info --base`.

## Three REST follow-ups (2026-10-04)

`CHAT-sdvmkidi`, `CHAT-pjymtozd` and `CHAT-sztbozcm`. Branch
`chat-rest-followups`. **This work is not merged.**
**Merged since: PR #180, `756d40b5`, on 2026-10-04.**

### An in-process miss answers 404

`KeyRefusalAdvice.inProcessNotFound` maps `NotFoundException` to 404 and keeps
its message. A single process REST launch runs the composite in the same JVM,
so its miss is `NotFoundException` and not `CoreNotFound`.

- `RestCoreNotFoundMappingTests` read 500 before the handler and 404 after.
- In a real launch, `PUT /topic/join/{id}` on a minted id with no stored room
  answered 500 at `04d8424d` and 404 on this branch.

### The `/topic` 404 was a slice reading too

`CHAT-znprrzhn` recorded that every `/topic` route answered 404 under method
security. A real launch at `447a1312`, the commit before the routing move,
read the bean type from `/actuator/beans`.

| Context | Proxy | `GET /topic/list` | `DELETE /topic/id/OTHER` without `REM` |
|---|---|---|---|
| default | `ChatTopicServiceController$$SpringCGLIB$$0` | 200 | **204** |
| `spring.aop.proxy-target-class=false` | `jdk.proxy2.$Proxy107` | 404 | 404 |
| this branch, default | CGLIB | 200 | 403 |
| this branch, JDK forced | `jdk.proxy2.$Proxy108` | 200 | 403 |

**Spring Boot forces class proxies in a real context, and a `@WebFluxTest`
slice does not.** So the slice met the hidden mapping, and a real launch met
the skipped facade check. The identity at `447a1312` was `Anon`, because the
`Agent` account did not exist yet.

**The routes and the checks now hold under both proxies.**

### The launch commands start again

- `shell-scripts/vector/gate-embedding-launch.sh` gained `--app.primary=REST`.
  Without it, the gate failed at startup on the missing
  `AgentResourceServerChain` bean. With it, the gate passes all 11 steps, with
  3 hits and `indexComplete` true.
- The `docs/EMBEDDING-PROVIDERS.md` command now carries the flag set of the
  gate. Run as written, it starts, and an agent token reads `GET /topic/list`
  with 200. Bisection shows three required additions: `app.primary`, the four
  `app.security` values, and `app.users.create` with `userinit.yml`. The
  `TypeUtil` failure of the earlier form is not traced.

### A correction of the previous section

`conda` works for activation. Only `conda info --base` and other plugin
subcommands fail.

## The core REST controllers refused on a REST launch (2026-10-04)

`CHAT-bnnkhgbd`. Branch `chat-bnnkhgbd-refuse-core-rest`. **This work is not
merged.**
**Merged since: PR #181, `63df02b6`, on 2026-10-04.** The owner reported the gap and chose the fix on 2026-10-04.

### The gap

A single process REST launch could mount the core REST controllers:
`persistence`, `index`, `key`, `secrets` and `pubsub`. None carries an access
check, and the REST chain requires the agent scope alone. Measured on
2026-10-04 with the `Agent` token: `PUT /persist/message/add` answered 201
with another user as sender, into a room where `POST /message/send` answered
403. So the route forged the sender and bypassed the `SEND` check.

The RSocket side refuses those routes with `ROLE_SERVICE` or `ROLE_ADMIN`
(`CHAT-rdlghoqe`). **An agent token never holds either role.** It carries its
scope authorities, and the agent identity carries `ROLE_AGENT`, even when the
agent username is `Admin`. So that rule would refuse every REST caller.

### The fix the owner chose: refuse at startup

`CoreRestControllers.requireAbsent` runs in `agentResourceServerChain`. Every
REST launch builds that bean, because `WebFluxSecurity` requires it. A switch
counts as `@ConditionalOnProperty` counts it: present and not `false`, so a
flag with no value refuses the start too.

Measured in a real launch: the earlier single process flag set now fails with
`A REST launch refuses the core REST controllers ... Remove
app.controller.persistence, app.controller.index, app.controller.key.`

### What moved with it

- **The vector gate seeds as a client does.** `POST /topic/new`, then three
  `POST /message/send/{id}`. The agent owns the room, so each send passes the
  `SEND` check. The gate passes all 11 steps.
- `docs/VECTOR-RECALL-API.md`: the seed uses the same routes, and the user
  recall reads the agent id from `GET /user/handle/{name}` at
  `.[0].key.key.id`. Two stale claims went with it. The send route does bind
  the principal on this chain, and a send does index the message.
- `docs/EMBEDDING-PROVIDERS.md` and the MCP appendix drop the three core
  switches. The embedding command, run as written, starts, and `POST /topic/new`
  answers 201.
- `docs/REST-TOKEN-RELAY.md` records it as GAP 3. Its drift lock binds
  `CoreRestControllers.kt` now.

### Not changed

- The `chat-build rest` facade mounts none of the five, so it is unchanged.
- Core launches mount the RSocket controllers alone, under the seam rule.

### Filed

`CHAT-dvuwfpmj`. The recall routes carry no access check either.
`/message/recall/global` searches every indexed message for any agent token.
Read from source. The owner decides the rule.

## The core access interfaces removed, the core routes sealed by role (2026-10-04)

`CHAT-wgdnjdio`, `CHAT-kdxglvtt` and `CHAT-zwopgvkx`, the three open children
of `CHAT-znprrzhn`. Branch `chat-core-access-seal`. **This work is not
merged.**
**Merged since: PR #182, `5de314b8`, on 2026-10-04.**

### The owner decision

**The role rule of `CHAT-rdlghoqe` is the boundary for the core routes.** No
per object check runs behind it. The owner chose this on 2026-10-04.

A per object check could not run as written. The `Service` account holds no
grant row, and the authorization server reads the core through it. So wiring
the checks would refuse that server on every read, unless `Service` gained
grants first.

### What was removed

No production class implemented any of these, so none of them ever ran.

- The package `com.demo.chat.security.access.core`: `PersistenceAccess`,
  `IndexAccess`, `PubSubAccess`, `TopicInventoryAccess`, `IKeyServiceAccess`,
  `SecretsStoreAccess`, and ten sub-interfaces. 39 annotations in total.
- Three members that lost their only caller: `hasAccessToEntity`,
  `hasAccessToSubmittedEntity`, and `EntityTargets`.
- `KeyVerifier.trustTypedStore`. Its one permitted caller was
  `hasAccessToEntity`.
- The tests that exercised only those: `LongByIdsFilterTests`,
  `EntityTargetsTests`, three cases of `MethodSecurityIntegrationTests`, eight
  of `VerificationBoundaryTests`, one of `AnonymousAuthorizationMatrixTests`,
  the pubsub half of `SendCheckExpressionTests`, and the `trustTypedStore`
  guard and cases of `KeyVerifierConstructionTests`.

**Two members stay, and they already had no production caller.**
`AccessBroker.permittedTargets` and the three argument
`SpringSecurityAccessBrokerService.hasAccessTo(who, target, perm)`. This work
did not orphan them, so it did not remove them.

### What was added

`CoreRouteAccessTests` holds one transport test per boundary. Each sends the
call as a caller with no credential and as a plain user, and each reads the
store after the refusal. A `Service` call on the same route is the control.

| Boundary | Refused routes | No effect, read after |
|---|---|---|
| persistence | `persist.user.add` | the user store holds no user |
| index | `index.user.add`, `index.user.query` | the user index finds no handle |
| pubsub | `pubsub.sendMessage` | a listener on the room hears only the service message |
| topic inventory | `pubsub.subscribe`, `pubsub.getUsersBy` | the room has no new member |
| key | `key.rem` | the key still exists |
| secrets | `secrets.add`, `secrets.compare` | the credential is unchanged |

**Before this work, `index.**` and `key.rem` had no transport test.**
Mutation: removing those two rules from `RSocketSecurityConfiguration` fails
exactly the index and key tests.

### Also corrected in this file

Six sections still said "not merged". Each now carries its merge record:
`CHAT-mfveaecc`, `CHAT-rdlghoqe`, `CHAT-lfaajjcj`, `CHAT-mpjtnpqv`, the REST
follow-ups, and `CHAT-bnnkhgbd`.

### Still open under `CHAT-znprrzhn`

`CHAT-esengqpv`, a second wildcard row for one target. It is the next PR.

**Read that line as of the merge of PR #182.** `CHAT-uxgdzpag` went first, by
the owner order. See the next section.

## Every Lucene index loads at start (2026-10-04)

`CHAT-uxgdzpag`. Branch `chat-uxgdzpag-index-reload`. **This work is not
merged.** The owner ordered it before `CHAT-esengqpv` on 2026-10-04.
**Merged since: PR #183, `5cc320e0`, on 2026-10-04.**

### Why it came first

`CHAT-esengqpv` adds a single owner guard. Its restart test failed: the second
start refused the `Admin` wildcard rows with `SecondOwnerException`.

**Each restart on a persistent store created a new `Admin`.** Measured on
2026-10-04 with `RedisGrantRestartTests`: the two starts wrote their rows
under two `Admin` keys. `InitialUsersService` finds an identity by its handle
through the user index, and only the auth index loaded from the store. So the
user index was empty, the handle lookup found nothing, and `addUser` made a new
user. The old `Admin` rows stayed live beside the new ones.

### What changed

`LuceneIndexBeans` registers a `StartupIndexLoad` for the user, topic, message,
membership and key-value indexes, beside the auth load. `RootKeyStartup` runs
every load before readiness, and the initial users start after it.

### Measured

`RedisGrantRestartTests` gained a test on node ids 16 and 17. The first start
adds a user, a room, a membership and a message. The second start reads each
through its reloaded index.

- With the five new loads disabled: `the Admin identity keeps its key across a
  restart ==> expected: <1556259905764130816> but was: <1556259919164936192>`.
- With them: 3 of 3 pass. The `Admin` key holds, one `Admin` user exists, and
  the room, the message and the member are found.

### An operator consequence

**A Redis store that already holds duplicate identity users fails the start
now.** The handle lookup finds more than one user. Measured with a probe that
was not kept: two `Admin` users in one store fail the start with
`Failed to start bean 'rootKeyStartup'`, caused by
`Source emitted more than one item`. The message does not name the user.
Recreate such a store. `docs/ARCHITECTURE.md` states it.

### Not measured

- The Cassandra backend. Its indexes are store backed, so it likely never
  lost them, but no restart test reads its users.
- The key-value load against a store of a type with no registered fields. That
  load fails the start by the rule of the key-value index.

### Next

`CHAT-esengqpv` rebases on this work. Its parked branch is
`chat-esengqpv-single-owner` at `a5b0b4c3`. **It did not rebase.** The
change was applied to a fresh branch, `chat-esengqpv-owner-guard`, cut from
`master` at `5cc320e0`, because the parked commit message says not to merge.
The parked branch is removed, local and remote, on 2026-10-04.

## One owner per target (2026-10-04)

`CHAT-esengqpv`, the last open child of `CHAT-znprrzhn`. Branch
`chat-esengqpv-owner-guard`, cut from `master` at `5cc320e0`. **This work is
not merged.**
**Merged since: PR #184, `d25ca495`, on 2026-10-04.** The merge has two
parents, and its tree is identical to the branch tip `d1b54b9d`.

### The owner decisions

1. **One owner, not one row.** An owner row is a live `*` row whose principal
   is an entity. A second owner is refused. A close row names a domain root,
   so it is not an owner row, and the guard lets it through. That keeps the
   close design of `CHAT-ylfxsthp` and the rank as they are.
2. **A repeat by the same owner writes nothing.** The write succeeds, and no
   second row is stored.
3. **A refusal is `SecondOwnerException`**, a `ChatException` that names the
   target. `TopicServiceImpl` already wraps a grant failure in
   `RoomOwnerGrantException`, so the room failure contract is unchanged.

### What changed

`CoreAuthorizationService.authorize` reads the stored rows of the target
through the target index before a write. It reads live rows alone, by the
rule of the summarizer: an expiry of 0, or an expiry in the future. A revoke
is unchanged.

**The check is not atomic with the write.** Two concurrent owner writes can
both pass it, as two concurrent room adds can.

### Why it waited for `CHAT-uxgdzpag`

The first restart test with the guard failed. Each restart on a persistent
store created a new `Admin`, and the guard refused the new `Admin` rows with
`SecondOwnerException`. `CHAT-uxgdzpag` reloads the user index at start, so
the `Admin` key holds, and the restart writes are repeats by the same owner.

### Measured

- `RoomOwnerGrantTests`: 8 tests. Five replace the old evidence test: a repeat
  writes no row, a second owner is refused, a close is written, an expired
  owner does not block a new owner, and a named grant is unaffected.
  Mutation: disabling the guard fails the repeat and the second owner cases.
- `RedisGrantRestartTests`: 3 of 3 against a real Redis, with the guard and
  the index reload together. Two starts hold one `Admin` wildcard row on a
  root, and no `SecondOwnerException` appears in the log.
- `build-health.sh --ci`: exit 0, 30 modules, 1947 tests, 0 failures,
  0 errors, 72 skipped, and no drift. The run rebuilt the shell image, and the
  image id moved from `sha256:b66d6f29` to `sha256:5321a9f0`.

### Not measured

- Cassandra restarts with the guard. `--ci` runs `CassandraGrantRestartTests`,
  but that test reads no `Admin` row count.

### What closes with it

Every child of `CHAT-znprrzhn` is done once this merges.
**Done on 2026-10-04.** `CHAT-esengqpv` is closed, and every child of
`CHAT-znprrzhn` is done.

## A message send on Cassandra (2026-10-04)

`CHAT-xcmpudyb`, the first step of `CHAT-aqpcacwv`. Branch
`chat-xcmpudyb-message-id`. **This work is not merged.**
**Merged since: PR #187, `2b1074ab`, on 2026-10-05.** The merge has two
parents. PR #188 carried the correction of the missing-output trap below.

### The defect, and four more on the same path

The long keyspace declared `msg_id TIMESTAMP` in `chat_message_id`,
`chat_message_user` and `chat_message_topic`. A `Long` id has no codec for that
type, so the first message write failed:

    Codec not found for requested operation: [TIMESTAMP <-> java.lang.Long]

**The issue text was wrong about the uuid keyspace.** It declares
`msg_id TIMEUUID`, and the uuid key generator makes time based ids. So the
uuid write worked.

**No test wrote a `Long` message id.** Every message repository and index test
used `UUID` keys.

The repair of the column type exposed four more defects. **Each one failed for
both key types.**

1. **The store removal named `msg_id` alone.** `msg_time` is a clustering
   column, so Cassandra refused the update with
   `Some clustering keys are missing: msg_time`. The removal reads the row
   first, then names both columns.
2. **The index stored the write time.** `MessageIndex.add` took
   `Instant.now()` for `msg_time`, so the index and the store gave two times
   for one message. It stores the message time now.
3. **The index removal could not run.** It deleted by `msg_id` alone, and the
   index tables partition on the sender and the room:
   `Some partition key parts are missing: user_id`.
4. **Room history on Cassandra was always empty.**
   `MessagePersistenceCassandra` did not override `byIds`, and the default
   answers `Flux.empty()`. So `listMessages` answered nothing, with no error.

### Two owner decisions of 2026-10-04

1. **The index removal reads a by-id table.** `chat_message_index_by_id`
   holds the sender, the room and the time of each message. It follows the
   `kv_pair_index_by_id` precedent. The removal reads it, then deletes each
   row by its full primary key.
2. **The store shape check compares column types.** A long store from before
   this change holds every name, so it started and failed at the first message
   write. It now fails the start with
   `chat_message_id.msg_id is timestamp, required bigint`. The required type
   follows the key type: `bigint` for `Long`, `timeuuid` for `UUID`. The
   no-migration rule of 2026-09-25 stands. Recreate such a store.

### A delete by entity cannot work on these tables

Each key class holds a regular column beside its key columns, such as
`topic_id` in the sender table. Spring Data puts every field of a key class in
the `WHERE` clause, so Cassandra refuses the delete with
`Non PRIMARY KEY columns found in where clause: topic_id`. Each removal names
the three key columns through a repository fragment. The key classes now map
`msg_time` as a clustering column.

### The evidence

- `CassandraMessageSendTests` sends a message on a `long` and a `uuid`
  Cassandra deployment, and reads it by id and by room. Node ids 25 and 26.
- `CassandraAuthorizationMatrixTests` sends a real message in place of a
  minted key.
- `MessageIndexCassandraTests` runs the index for both key types. It reads the
  time, removes a message, and keeps the other message of the room.
- Six mutations, each run alone. Each made the tests fail that were written
  for it, and each file was restored by absolute path.
- `build-health.sh --ci`: exit 0, 30 modules, 1969 tests, 0 failures,
  0 errors, 72 skipped, and no drift. The image id moved from
  `sha256:ce27764f` to `sha256:aa3058ff`. The default mode: 1629 tests,
  37 skipped, and no drift.
- **The first `--ci` run failed once in `chat-service-composite`.**
  `MessageReindexServiceImplTests` read `Expected size: 2 but was: 1`, and the
  list it printed held two elements. So the read ran during the write. This
  branch does not change that module, and the second run passed. One kill
  event fell in the first run window.

### Traps found, each of which cost a cycle

- **Compiled output went missing twice, and the cause is unknown.** At 15:00
  and 15:01, two runs failed with `NoClassDefFoundError`. The compiler ran in
  full and printed its usual warnings, and the test class loaded. Afterward,
  `target/classes` and `target/test-classes` of `chat-persistence-cassandra`
  held no `.class` file, and they held copies of the `.kt` sources. Both runs
  failed loudly. **Do not read this as a daemon defect.** A third run with
  `-Dkotlin.compiler.daemon=false` worked, but that is one run. Four later
  runs with a new daemon, two of them the exact failing commands, all worked.
  The poms copy no `.kt` file. IntelliJ built nothing that day. Sigma's
  worktree did not exist yet, and its Codex session only read files. The
  daemon keeps three log files of 1 MB, so the log of 15:00 is gone. If it
  happens again, keep the evidence before the next build. `CHAT-lantftth`
  adds a procedure for that, and a gate check that fails when a module runs
  no test.
- **Two sessions on one Docker VM kill Cassandra.** Docker events show
  `cassandra:4.1.3` killed for memory, exit 137, while a second session ran
  container tests. Every Cassandra call then failed with
  `NoNodeAvailableException`. Read `docker events --filter event=oom` before
  you trust a red container run.
- **A wait loop on `pgrep -f` matches its own shell**, because the pattern is
  in its command line. Write the pattern as `[m]ultiModule...`.

### Filed

- `CHAT-mpfuifei`. `TopicPersistenceCassandra` has no `byIds` either. No
  composite service calls it today.

### Not measured

- **A packaged Cassandra deployment.** No launched jar or image sent a
  message. Every proof starts the composition in a test JVM against a test
  container.

## A readable chat log in the shell (2026-10-05)

`CHAT-bmmtojqm`, under `CHAT-aqpcacwv`. Sigma owned it. PR #186 merged on
2026-10-05 as `7089a3fb`. The merge has two parents, `0fd74682` and
`925a1db8`.

- `messages <room>` shows the stored time, the sender handle and the text,
  oldest first. `--limit N` selects the newest N messages and keeps that
  order. A missing user shows its key id. The `SUBSCRIBE` check stays.
- **Both Jackson decoders dropped the stored timestamp.** The server sent it,
  and the decoders put the current time in its place. The first integration
  run found it. A separate decoder commit repairs both generations and keeps
  decimal precision. Whole numbers read as seconds by default.
- The PR records `build-health.sh --ci` exit 0 on its final run: 30 modules,
  1962 tests, 0 failures, 72 skipped.

## The test-count check in build-health (2026-10-05)

`CHAT-lantftth`. PR #188 merged on 2026-10-05 as `a1bda6f3`. The merge has two
parents, `2b1074ab` and `cb794d95`.

- **The gate now fails when a listed module runs no test.** It compares the
  modules that ran tests with `shell-scripts/build-health-tests-unit.txt`
  (28 modules) and `shell-scripts/build-health-tests-integration.txt`
  (30 modules). `NO TESTS` and `UNLISTED` both exit 1. `--record` writes the
  list of a mode from a run.
- `shell-scripts/capture-build-evidence.sh` keeps the evidence of a missing
  build output before the next build. B13 in `docs/BUILD-HEALTH.md` records
  the procedure.
- The PR corrected the `CHAT-xcmpudyb` trap text. The cause of the missing
  output is unknown, and the old text claimed more than the evidence showed.
- **The check does not compare test counts.** A module that runs one test in
  place of a hundred still passes.

## More than one agent on one REST deployment (2026-10-05)

`CHAT-frcrctdp`, under `CHAT-aqpcacwv`. PR #189 merged on 2026-10-06 as
`42f0bb4b`. Its parents are `a1bda6f3` and `1f94246d`. The master tree matches
`1f94246d`, and local master matches `origin/master`. The issue and all ten
subtasks are done. The local feature branch is deleted. Spec:
`docs/superpowers/specs/2026-10-05-multi-agent-rest-design.md`. Plan:
`docs/superpowers/plans/2026-10-05-multi-agent-rest.md`. Sigma reviewed the
design and the plan before any code.

### What changed

- `app.security.agents[n]` and `app.security.required-scope` replace
  `app.security.agent.*`. The old keys fail the start and name the new keys.
- The token `client_id` selects the agent identity, on REST and on the core.
  An unlisted client answers 401 on REST and `0x401` on the core.
- `chat-build --agent CLIENT_ID=HANDLE` repeats once per agent. On `core` it
  also creates the agent user, in the bracket form
  `app.init.initial-users[<H>]`. The old agent flags exit 2.
- The authorization server issues one `client_credentials` client per agent.
  It prints each generated secret once, after the repository holds the client.
  Tokens are self-contained with a 300 second lifetime. `chat-client` lost
  `chat.mcp`.
- `shell-scripts/agent-http-gate.sh` runs the agent HTTP test and the relay
  test in their own builds. The PR job `agent-http` and
  `build-health.sh --ci` call it.

### Rules that are easy to lose

- **Handle checks ignore case.** The Lucene user index lowercases the handle,
  so a lookup for `admin` answers `Admin`. The lifecycle keeps an exact match.
  `Admin`, `Anon` and each service account are reserved.
- **The core and REST must carry the same list.** No check compares them.
- **The memory profile makes new agent secrets at each start.**
- **A map key without brackets can lose distinct values.** Measured: with the
  emitter mutated to drop the brackets, `Bot_1` bound to the handle `Bot1`.
- **Neither reactor build runs the agent HTTP classes.** CI and `build-health.sh`
  activate neither `expose-webflux` nor `rest-core-e2e`. Only
  `agent-http-gate.sh` runs them, and it requires zero skipped tests.
- **The vector gate agent is `Agent`, not `Admin`.** `Admin` is reserved now.

### Measured

- PR #189 CI run `37425003150` passed. The `build`, `integration`, and
  `agent-http` jobs all passed.
- `RestAgentSelectionTests` 2/2 with 0 skipped under `-Pexpose-webflux`.
  `CoreAgentSelectionTests` 2/2. `RestToCoreBearerDeploymentTests` 7/7 with 0
  skipped after `clean verify -Prest-core-e2e`. The relay core jar holds no
  `chat-webflux`.
- A first-agent mutation of the REST wiring failed the REST test alone, and a
  first-agent mutation of the core wiring failed the core and relay tests
  alone.
- `agent-http-gate.sh --offline`: ok at baseline, failed with 2 skipped
  without the profile, failed with 2 failures under the REST mutation, ok
  restored.
- A `REFERENCE` token format failed the agent decoder test. Moving the secret
  print before the save failed both output tests.
- `test-flags.sh` 30/30. `gate-embedding-launch.sh` passed with `Agent`: 3 hits
  and `indexComplete` true.
- Sigma's branch review found that empty values for the removed agent flags
  bypassed the refusal. `1f94246d` compares both values with `None`.
  `test-flags.sh` passed all 32 cases, including both empty-value refusals.
- `build-health.sh` default mode at `95eb1716`: exit 0, 30 modules, 1703 tests,
  0 failures, 41 skipped, and no drift.
- `build-health.sh --ci` at `95eb1716`: exit 0, 30 modules, 2043 tests, 0
  failures, 76 skipped, no drift, and `agent http gate: ok`. The image id moved
  from `sha256:e819f9db` to `sha256:522e3ebe`.
- `build-health.sh --ci` with the REST wiring mutated: exit 1. The reactor part
  passed with 0 failures. The agent gate failed `RestAgentSelectionTests` with 2
  failures. **No reactor test reads the REST wiring over HTTP.**
- The self-review found that a REST start with only the old keys failed in the
  decoder bean with `app.security.required-scope is required.`, before the
  guard ran. `AgentSecurityProperties` now binds the old `agent` block to refuse
  it, so every call site names the new keys. `95eb1716`.

### Not measured

- Secret rotation, and an authorization server restart while an adapter runs.
- A check that the REST list and the core list agree. No such check exists.

### Filed

- `CHAT-uizwrxmf` recorded the missing `client.json` that prevented
  `chat-build authserv` from starting under `client-init`. PR #191 closed
  this defect. See the next section.

## Authorization server client initialization repair (2026-10-06)

PR #191 merged as `edf67e88`, with parents `a2350413` and `6cc554aa`.
The merged tree matches reviewed head `6cc554aa`. Local master matches
`origin/master`. `CHAT-uizwrxmf` is done.

- `chat-build authserv` passed `--clientpath='classpath:client.json'`, and no
  `client.json` has ever existed in this repository. So a `client-init` start
  always failed. `chat-build` now passes no `--clientpath`. The option remains
  available.
- `client-init` now saves `app.oauth2.client` once. It also saves Spring
  clients, configured agents, and an optional JSON client. A blank app client
  ID fails startup.
- Agent collision checks compare client IDs and row IDs. Both remaining
  repository save calls reject a row ID held by another client before writing.
- **A JDBC `save` selects INSERT or UPDATE by the row id, not the client id.**
  An agent row id equals its client id, and the shipped app client has the row
  id `chat-client`. So a client whose row id the store held for another client
  updated that row in place. The review reproduced it through the production
  JDBC repository with mocked JDBC operations.
- `ClientInitializerTest` ran 22 tests with 0 failures and 0 skipped. Each new
  guard has a mutation that failed only its own test. The independent review
  reported 38 focused tests passed.
- The default local gate passed with 1712 tests, zero failures, 41 skipped,
  and no drift. CI run `37508963649` passed `build`, `integration`, and
  `agent-http` on `6cc554aa`.
- A live `client-init` start against the JDBC client store remains untested.
- `CHAT-pnjmfhnb` remains open at low priority. It tracks the intermittent
  `MessageReindexServiceImplTests` failure when the test checks records before
  the terminal record is published.

## The initial grants are a seed (2026-10-07)

`CHAT-ghwtzgjp`. Branch `chat-ghwtzgjp-initial-grants`. **This work is not
merged.**
**Merged since: PR #196, `5e325499`, on 2026-10-08.**

### The defect

`InitialUsersService` wrote every initial grant at every start. A named grant
is not an owner row, so the owner guard of `CHAT-esengqpv` did not stop it.
Each restart added one copy of each named grant. Measured on 2026-10-07: two
starts on one Redis store added 9 rows.

**A copy did not change an access decision on its own.** It could defeat a
revoke. An operator who expired one copy left the other copies live, and a
`Long` copy from a later start ranks above the edited row.

### The owner decisions of 2026-10-07

1. **The fix sits in `InitialUsersService`.** Other grant writers do not
   change.
2. **A revoked initial grant stays revoked across a restart.** An expired or
   a muted row counts as present.
3. **Stored copies collapse to the row that decides access.** The start keeps
   the highest key id, which `AuthSummarizer` selects among tied copies.

`docs/ANONYMOUS-AUTHORIZATION.md` states the rule under
`The initial grants are a seed`. `drift.lock` binds that document to
`InitialUsersService.kt` now.

### Measured

- `InitialGrantSeedTests`: 4 tests. Three mutations each fail their own test:
  always write, count live rows only, and keep the lowest key.
- `RedisGrantRestartTests` gains a restart test on node ids 30 and 31. It
  fails against the `master` service with 4 or more `JOIN` rows.
- `CassandraGrantRestartTests` asserts one row per named initial grant. It
  fails against the `master` service at `Anon -> User : FIND`. **So the
  Cassandra backend also wrote copies.** The issue had recorded Cassandra as
  not measured.
- `build-health.sh --ci`: exit 0, 30 modules, 2144 tests, 0 failures,
  0 errors, 76 skipped, no drift, and `agent http gate: ok`. The run used an
  empty `DOCKER_CONFIG`, and the image id moved from `sha256:1afdaa16` to
  `sha256:f5ee6fce`.
- `drift check` reports ok, and `git diff --check` exits 0.

### Not covered

- **The read is not atomic with the write.** Two processes that start at the
  same time against one store can both write a grant.

## The RSocket authentication seam (2026-10-07)

`CHAT-jkordfef`. Branch `chat-jkordfef-rsocket-seam`. **This work is not
merged.**
**Merged since: PR #197, `6df0680e`, on 2026-10-08.**

### The gap

An authentication entry with an auth type that is not well known took the
`Anon` identity in silence. `AuthenticationPayloadExchangeConverter` in
spring-security-rsocket 7.0.7 answers no authentication for such an entry, so
the anonymous filter ran. The legacy `basic` MIME type had the same result.
Measured on a running server: the room was created.

### The owner decisions of 2026-10-07

1. **A caller that sends a credential never takes `Anon`.** An unsupported
   credential is refused. `UnsupportedCredentialRefusal` runs at payload
   order 199, one step before authentication. A request answers `0x401`, and a
   setup frame answers `RejectedSetupException`.
2. **Expired and invalid bearers share `0x401`.** No distinct code or
   identity exists for an expired credential.

`docs/IDENTITY-POLICY.md` holds the seam table. `drift.lock` binds it to the
seam sources now.

### Measured

- `RSocketAuthenticationSeamTests` in `chat-deploy-memory`: 11 cases on a
  running server, nothing mocked. All pass. With the refusal removed, exactly
  the three unsupported cases fail.
- `build-health.sh --ci`: exit 0, 30 modules, 2155 tests, 0 failures,
  0 errors, 76 skipped, no drift, and `agent http gate: ok`. The image id
  moved from `sha256:4965580b` to `sha256:63c3d5c6`.
- `CHAT-ileqgajf` is closed. Its interceptor was gone, and the new test pins
  the anonymous path.

### One trap

**A port-0 test server can collide with IntelliJ on macOS.** The first `--ci`
run gave a `chat-client-rsocket` test server port 63342. IntelliJ listens on
`127.0.0.1:63342`. Netty bound the wildcard address, and macOS allowed both.
The client connected to `localhost` and reached IntelliJ, which closed the
connection. `KeyServiceRequesterTests` failed 3 of 3 with
`ClosedChannelException`, and maven skipped `chat-shell`. The class passes
alone. If a test server logs port 63342, read `lsof -iTCP:63342` before you
read the failure as a regression. `CHAT-cikgeefc` records it.

## Merges from PR #193 to PR #202 (2026-10-06/09)

Each merge has two parents. Each merge tree equals its branch tip, except
PR #201. Measured on 2026-10-09 with `git diff <tip> <merge>`.

| PR | Merge | Issue | What |
|----|-------|-------|------|
| #193 | `65cc66e7` | `CHAT-pnjmfhnb` | Wait for the durable terminal record in `runAndAwait` |
| #194 | `506cb1dd` | `CHAT-ybtirmgj` | Store the Lucene indexes in files, reuse them at start, else rebuild |
| #195 | `3bb8a85d` | `CHAT-eesnvnad` | `chat-build core --index-root` sets `app.index.lucene.root` |
| #196 | `5e325499` | `CHAT-ghwtzgjp` | Seed each initial grant once, and keep a revoke across restarts |
| #197 | `6df0680e` | `CHAT-jkordfef` | Refuse an unsupported RSocket credential |
| #198 | `e66d2257` | `CHAT-zcxgrtqc` | Enforce typed operation policy schema boundaries |
| #199 | `b8d14c71` | `CHAT-xdsetfkf` | The typed action grant policy model |
| #200 | `d0b5eee4` | `CHAT-qojwcatx` | Execute the grant policies with the shipped writers |
| #201 | `e8d9ed13` | `CHAT-chftimgy` | Message command bus Stage 1 |
| #202 | `02baae05` | `CHAT-ugsreefu` | The Admin-only message import route |

**The PR #201 merge tree differs from its tip `165c7f3f`, and that is
expected.** The tip does not hold PR #198, PR #199 and PR #200. The files in
the difference are exactly the files those three merges changed.

Five of these merges have no section of their own in this file: #193, #194,
#195, #198 and #201. Their specs, plans and issues hold the record.
`docs/MESSAGE-COMMANDS.md` describes Stage 1 of the command bus.

### One CI reading after PR #202

**The `build` job of run `37976198743` failed.** That run tested the PR #202
head `e789acaa`. `MessageCommandServiceTests` case 21 failed with a
`NullPointerException` at line 92. PR #202 did not change that test. The
`integration` and `agent-http` jobs passed.

Read from source and not measured: the test waits until the key registry holds
the message key. `P` registers the key and then stores the message, so the read
can fall between the two steps. `CHAT-udhhlrrb` holds it.
**Merged since: PR #204, `d8e8c8ee`, on 2026-10-09.** The test now waits for
`P` to succeed before case 21 reads the message. `CHAT-udhhlrrb` is done.

## The action grant policy model (2026-10-08)

`CHAT-xdsetfkf`. Branch `chat-xdsetfkf-action-grant-model`. **This work is not
merged.** Spec: `docs/superpowers/specs/2026-10-02-action-grant-policy-model-design.md`.
**Merged since: PR #199, `b8d14c71`, on 2026-10-09.**

### What exists

- A typed model in `chat-core`, package
  `com.demo.chat.service.security.policy`. `GrantPolicy.intents` returns
  `GrantIntent` values. It reads no store and writes no row.
- `ShippedGrantPolicies` states the shipped writers as three policies:
  `roomowner`, `roomjoin`, and `roomleave`.
- **No production code reads the model.** `ContextRoomOwnerGrant` and
  `MembershipGrant` did not change. `CHAT-qojwcatx` moves them onto it.

### Rules that are easy to lose

- **`NONE` and `NOW` are policy values, not stored values.** The shipped
  writers store `NONE` as `0` and `NOW` as the clock time of the leave.
- **`ACTIVE` and `ROOT` are runtime sources.** Configuration does not accept
  them.
- **The constructor refuses `*` outside the room owner rule.** So the draft
  close row is refused. A close policy needs an owner decision first.
- **A `GrantIntent` holds no key.** The writer selects the grant row key.

### Measured

- `GrantPolicyTests` 15 of 15 in `chat-core`.
- `ShippedGrantPolicyTests` 8 of 8 in `chat-security`. Each test runs the
  production writer against a map store, and compares the rows with the
  policy intents.
- `MembershipGrantTests` 9 and `RoomOwnerGrantTests` 8 pass with no change.
- Three mutations of `ShippedGrantPolicies`, each run alone. Removing the join
  `SUBSCRIBE` rule, giving the leave `NONE`, and granting the `User` root on a
  join each failed exactly its own comparison test.
- `build-health.sh` default mode: exit 0, 30 modules, 1839 tests, 0 failures,
  0 errors, 41 skipped, and no drift. `drift check` reports ok, and
  `git diff --check` exits 0.

## The action grant policy execution (2026-10-08)

`CHAT-qojwcatx`. Branch `chat-qojwcatx-policy-execution`, stacked on PR #199.
**This work is not merged.** Plan:
`docs/superpowers/plans/2026-10-08-action-grant-policy-execution.md`.
**Merged since: PR #200, `d0b5eee4`, on 2026-10-09.** PR #199 merged first,
so PR #200 merged against `master`.

### What changed

- `PolicyGrantWriter` in `chat-security` maps policy intents to `AuthMetadata`
  rows. It holds the storage rules that `MembershipGrant` held before.
- `ContextRoomOwnerGrant` applies `roomowner`. `MembershipGrant` applies
  `roomjoin` and `roomleave`. Both constructors keep their parameters.
- `GrantPolicyConfiguration` registers one writer, and it enables
  `ShippedGrantPolicies.ALL` alone. **A policy that is not in that list has no
  effect.**
- `TopicServiceImpl` did not change. It calls the two ports, as before.

### Rules that are easy to lose

- **The member of a join is the active principal, not the caller.** A caller
  that holds `JOIN` on another member can join that member.
- **Each new row mints its own `AUTH_METADATA` key.** A target key never
  becomes a grant row key.
- **A new `*` row still meets the owner guard.** The writer calls
  `AuthorizationService.authorize`.
- **One unreachable edge case changed.** An expired owner row of the same
  caller is now set to never expire in place. Before, that case wrote a second
  row. A new room key has no stored row, so `addRoom` never meets it.
- **No authorization topic exists.** The writer does not need one.

### Measured

- `PolicyGrantWriterTests` 11 of 11.
- The five named tests pass with no change to their assertions:
  `AnonymousAuthorizationMatrixTests` 40, `RoomOwnerGrantTests` 8,
  `MembershipGrantTests` 9, `TopicServiceMemberGrantTests` 6, and
  `StandardUserJoinSendTests` 3. The three grant wiring boot tests pass too.
- Three mutations of `PolicyGrantWriter`, each run alone. Removing the
  enablement check failed the two enablement tests. Using the target key as the
  row key failed the key root test and eight row tests. Writing `0` on a leave
  failed four leave tests.
- `build-health.sh --ci`: exit 0, 30 modules, 2193 tests, 0 failures,
  0 errors, 76 skipped, no drift, and `agent http gate: ok`. The run used an
  empty `DOCKER_CONFIG`. The image id moved from `36ac0b967c84` to
  `5d334eed0cfa`. `docker events` reported no OOM kill in the run window.
- `drift check` reports ok after a reviewed relink of
  `docs/ANONYMOUS-AUTHORIZATION.md`. Its bound seed passage did not change.

## The message import route (2026-10-09)

`CHAT-ugsreefu`. The implementation is in the worktree branch
`chat-ugsreefu-message-import`. It is not committed.
**Merged since: PR #202, `02baae05`, on 2026-10-09.** The merge has two
parents, and its tree equals the branch tip `e789acaa`. `CHAT-ugsreefu` is
done, and `CHAT-emwhnlay` is unblocked.

### Contract

- The composite RSocket route is `message.message-import`. It accepts only an
  Admin caller.
- The route preserves the supplied registered user and the supplied time.
  `Anon` is not a valid imported sender.
- The route uses `import:<source-message-id>` as the command request ID.
  History matching also prevents a repeat with a new request ID.
- The history match uses sender, millisecond time, and text. This remains a
  read-before-write check and does not prevent concurrent duplicate imports.
- Publication is disabled by default. The `publish` flag enables the `U`
  obligation. The caller does not report success for `PENDING` or `INCOMPLETE`.
- History is ordered by time and then by the required type-aware key
  comparison. Normal message times also truncate to milliseconds.

### Evidence and limits

- Memory route tests cover ordered import, restart-style history repeat,
  Admin access, anonymous access, `Anon` sender refusal, unknown keys, and
  future time.
- Cassandra ordering coverage is added to `CassandraMessageSendTests` for
  long and UUID deployments.
- The Cassandra test was not run in the local review. Container availability
  and the measured result remain pending.
  **CI measured it since.** Run `37976198743` on `e789acaa`: the
  `integration` job ran `CassandraMessageSendTests` with 4 tests and
  0 failures.
- `docs/MESSAGE-COMMANDS.md` and this register describe the new route.

## MCP Stage 1 messaging on the implementation branch, 2026-10-09

Issue: `CHAT-teujorxl`. Branch: `chat-teujorxl-mcp-messaging`.
This section records branch work, not a merge.
**Merged since: PR #205, `8965daa0`, on 2026-10-09.** `CHAT-teujorxl` is
done. The worktree and the branch are gone. PR #206 then corrected the MCP
records in `docs/BUILD-HEALTH.md` and the plan, under `CHAT-ppyxzpqo`.
The accepted messaging spec is `docs/superpowers/specs/2026-10-09-mcp-messaging-design.md`.
The execution plan is `docs/superpowers/plans/2026-10-09-mcp-messaging.md`.

The adapter exposes five read tools by default and adds sending with `enableSend=true`.
Message tools use the existing submit and status routes.
Request identity belongs to the authenticated agent across its adapters and rooms.
Stage 1 retry safety ends when the server process restarts.
The default `P,I` requirement does not prove vector completion or recipient delivery.

The adapter checks every returned message's room.
This check is necessary because the shipped message-by-ID route permits reads from any authenticated agent.
**Read that sentence as of 2026-10-09.** The server checks `SUBSCRIBE` on the
room of a message read by ID since `CHAT-erkoxybm`, so the adapter check is a
second guard now.
Room setup requires creation or `PUT /topic/join/{id}`, followed by successful history and submission probes.
The agent has no grants-read route.
Operator schemas, error metadata, and limits are in `docs/MCP-ADAPTER.md`.

Task 6 is committed at `1b5e73ff`.
Follow-up `e2c77a7d` also tests a changed room under the same request identity.
Its six authenticated MCP deployment tests pass without failures, errors, or skips.
The agent HTTP gate also passes two identity tests and seven relay tests.
The REST run emitted no Surefire shutdown warning.
`CHAT-gsddauhn` holds the separate full REST-profile failure.
The earlier full RSocket-profile shutdown warning has no measured cause.

The default gate ran 2,079 tests in 30 modules, with zero failures, zero errors, and 47 skips.
The `--ci` gate ran 2,450 tests in 30 modules, with zero failures, zero errors, and 82 skips.
Both gates exit 0 and report no drift.
The `--ci` run used an empty temporary `DOCKER_CONFIG`, after the first run failed in `build-image`.
The six `McpMessagingDeploymentTests` tests skip in the reactor, so the `--ci` skip count rose from 76 to 82.
Surefire printed its shutdown deadline in `chat-client-rsocket` and `chat-deploy-memory` during the default gate.

Final review found that the send tool declared `idempotentHint=true`, against the spec.
Task 7 sets it to `false`, because retry safety ends when the server process restarts.
The full gates measured the tree before that one-value change.
After it, the 237 `chat-mcp` tests and the 15 agent HTTP gate tests passed.

Kafka durability, replication, search, and live subscriptions remain outside this milestone.
The Admin-only import route of `CHAT-ugsreefu` merged separately as PR #202, as recorded above.
MCP exposes no import tool and no sender override.
This branch merged `origin/master` at `23f8f3a0` before its pull request.

## One Cassandra test container per JVM (2026-10-09)

`CHAT-znodyvcc`. Branch `chat-znodyvcc-cassandra-nonode`. **This work is not
merged.** `docs/BUILD-HEALTH.md` holds the measurements, under
`Cassandra test containers and Docker memory`.
**Merged since: PR #207, `867d614e`, on 2026-10-09.** `CHAT-znodyvcc` is done.

### The cause, reproduced

- `CassandraTestContainerConfiguration` started one container for each Spring
  test context. `chat-persistence-cassandra` has 10 contexts, so 10 containers
  ran at once, about 15 GiB on a 16 GiB Docker VM. `chat-index-cassandra`
  reuses that configuration through the test jar.
- Each container sized its heap from the VM: `-Xmx3930M`.
- Baseline master passed alone. With a 5 GiB ballast container on the VM, it
  failed with 5 `NoNodeAvailableException` errors, the signature of the issue,
  and Docker reported 3 out-of-memory events. **The issue recorded no
  out-of-memory event for its own failing run.** So the reproduction matches
  the error signature, and not that one detail.

### What changed

- `SharedCassandraContainer` starts one container for each test JVM, with both
  keyspaces. The `embeddedCassandra` bean returns it, with an empty destroy
  method, because Spring infers `close` on an `AutoCloseable` bean.
- Both Cassandra test containers set `MAX_HEAP_SIZE=1G` and
  `HEAP_NEWSIZE=256M`. `withReuse(true)` is removed. It had no effect, because
  `~/.testcontainers.properties` does not enable reuse.
- `NodeClaimTableProbeTests` claims node ids 210 to 212. It claimed 201 to 203
  before, and its live lease on 201 failed
  `CassandraNodeIdClaimStoreTests` once both shared one keyspace.

### Measured

- Under a 5 GiB ballast: baseline 5 errors, the change 0 errors, with 1
  container at peak and no out-of-memory event.
- Without ballast, the run-3 shape of the issue on the change: every module
  passed, with 1 Cassandra container at peak. `chat-persistence-cassandra`
  took 5:51, against 7:15 on baseline.
- `CassandraStoreShapeCheckTests` still takes about 285 s. `CHAT-pggtduxz`
  holds it. **Since `CHAT-pggtduxz`, the class takes 80.5 s.** See the next
  section.

## The shape check tests build small keyspaces (2026-10-10)

`CHAT-pggtduxz`. Branch `chat-pggtduxz-shape-check-speed`. **This work is not
merged.** Test code only.
**Merged since: PR #208, `dfdf7b2d`, on 2026-10-09.** **`CHAT-pggtduxz` is
still `todo`, measured on 2026-10-09.** Its close step did not run.

- `CassandraStoreShapeCheckTests` built a full keyspace from
  `keyspace-long.cql` in 11 of its 12 tests, about 26 schema statements each,
  and dropped none of them.
- **Only `a complete keyspace passes` builds the full keyspace now.** It proves
  that the real script satisfies the full required map.
- Each refusal case builds the tables it names, from the same script
  statements, and limits the required map to those tables. A table name that
  matches no statement fails the test.
- **Each refusal case first passes the check**, so the removed element alone
  causes the refusal. The `TIMESTAMP` case passes with the script type before
  it gets the old type.
- Each test drops its keyspace. The module shares one Cassandra container since
  `CHAT-znodyvcc`.

**The check evaluates each required table and column on its own.**
`ColumnShapeCheck` reads that way, so a smaller required map gives the same
verdict for the removed element.

### Measured

- The class alone, with `-Pintegration`: 296.6 s before, 80.5 s after.
  12 tests, 0 failures.
- Three mutations, each run alone and restored from git. The check ignores
  the `root` column: only the `keys.root` case fails. The small keyspaces get
  no table: the 10 cases with a pass check fail at it. The type check is off:
  only the `TIMESTAMP` case fails.
- `chat-persistence-cassandra` alone: 2:17, against 5:51 after `CHAT-znodyvcc`
  and 7:15 before it. 131 tests, 0 failures.
- `build-health.sh --ci` with an empty `DOCKER_CONFIG`: exit 0, 30 modules,
  2460 tests, 0 failures, 0 errors, 82 skipped, agent HTTP gate ok, and no
  drift. The class took 71.1 s in that run. The shell image moved from
  `033f847c8150` to `c15e101bac92`, and Docker reported no OOM event.

## Merges from PR #203 to PR #209 (2026-10-09)

Each merge has two parents. Measured on 2026-10-09 with
`git rev-list --parents` and `git diff <tip> <merge>`.

| PR | Merge | Tip | Issue | What |
|----|-------|-----|-------|------|
| #203 | `23f8f3a0` | `3ada260a` | `CHAT-ukiwartp` | Refresh the forward register after PR #202 |
| #204 | `d8e8c8ee` | `c93e94a3` | `CHAT-udhhlrrb` | Wait for `P` to succeed before case 21 reads the message |
| #205 | `8965daa0` | `748a1348` | `CHAT-teujorxl` | MCP Stage 1 messaging: history, message read, submit, and command status |
| #206 | `4ad96a27` | `a2357141` | `CHAT-ppyxzpqo` | Correct the MCP completion and gate records |
| #207 | `867d614e` | `889cc05f` | `CHAT-znodyvcc` | One Cassandra test container per JVM, with a capped heap |
| #208 | `dfdf7b2d` | `3478bc91` | `CHAT-pggtduxz` | Small keyspaces in the store shape check tests |
| #209 | `69dbd2ee` | `94a280fd` | `CHAT-okcfryup` | The reindex test job list is safe for a concurrent reader |

**Five merge trees equal their tips. Two differ, and each difference is
expected.**

- The PR #205 tree differs from `748a1348` in two test files:
  `MessageCommandServiceTests.kt` and `MessagingStack.kt`. PR #204 changed
  exactly those two files, and the branch merged `origin/master` before #204.
- The PR #207 tree differs from `889cc05f` in two files:
  `docs/BUILD-HEALTH.md` and the MCP messaging plan. PR #206 changed exactly
  those two files, and the branch was cut before #206.

### The concurrent job list read (2026-10-09)

`CHAT-okcfryup`. PR #209. `FakeJobStore.written` in
`MessageReindexServiceImplTests` was an `ArrayList`. The reindex run appends to
it on its own thread, and `awaitDurableOutcome` iterates it on the
`Flux.interval` thread. An append during that iteration raised
`ConcurrentModificationException` in a default `build-health.sh` run on
2026-10-07.

**The reported line 758 is past the end of the 670-line file.** So it is the
Kotlin inline line of `lastOrNull`, and that agrees with the race. The list is
a `CopyOnWriteArrayList` now. Test code only.

**The failure was not reproduced before the change.** The class passed 11 runs
of 11 after it, with 23 tests each. Those passes do not prove the race is
closed. A later failure of this test reopens the issue.

### Open after these merges

- **`CHAT-pggtduxz` closed.** PR #208 merged its work, and the issue is `done`
  as of 2026-10-11. The owner closed it.
- **`CHAT-gsddauhn` is still `todo`.** It holds the full REST-profile failure
  that the MCP section names.

## Message pointers (2026-10-10)

`CHAT-erkoxybm`. Branch `chat-erkoxybm-message-pointer`. **This work is
merged.** PR #213 merged on 2026-10-11 as `dc116ea7`, a merge commit with two
parents, `3e8d8b03` and `5c24e6e9`. The merge tree is byte-identical to the
branch tip `5c24e6e9`, because `git diff 5c24e6e9 dc116ea7` was empty. The
branch is deleted, local and remote. Spec:
`docs/superpowers/specs/2026-10-10-message-pointer-design.md`. Plan:
`docs/superpowers/plans/2026-10-10-message-pointer.md`.

A pointer is a message that names one message or one topic. It carries no
text. It enters through a new `pointer` operation on `ChatMessageService`.

### The eleven owner decisions

1. A pointer lives in a host room. `key.from` is the creator and `key.dest` is
   the host room. The field `target` names the target.
2. `FORWARD` is a new permission on the host room. It is not a domain and it
   has no root key.
3. The creator must read the target. An unreadable target answers the same
   error as a missing target.
4. A pointer uses the backends `P`, `I` and `U`. It never uses `V`, so recall
   never returns it.
5. One nullable `kind` column marks a pointer row. It holds the target domain
   name.
6. A pointer to a pointer is refused at creation. No stored chain can exist.
7. No reader fetches the target. A reader shows the target domain and id.
8. `messageById` checks `SUBSCRIBE` on the `dest` of the returned message.
   **The rule applies to every message, not to pointers alone.**
9. A topic target uses `SUBSCRIBE`. It does not use `GET`.
10. Tests cover the persistence stores, the creation checks, the `messageById`
    rule, and one MCP decoder test. Routes, the index and the shell get no
    pointer test.
11. The message value type stays `String`. A pointer is `Message<T, String>`
    with `data` of `""` and a separate `target`.

**Decision 11 replaced a decision of the same morning.** That one bound the
value type to `Any`, and it needed 206 test sites in 96 test files.

### The `messageById` change

`messageById` reads the message, and then checks `SUBSCRIBE` on its room.
**This is a behavior change for every message.** The `{Anon, Message, GET}`
row left `userinit.yml`. A caller that reads a message by id from a room it
did not join answers 403.

The two `GET` sites moved together: the RSocket composite and the REST
mapping. `MessageByIdRoomRuleTests` and `MessageByIdRestRuleTests` pin them.

**One existing test asserted the old behavior.**
`McpMessagingDeploymentTests` read a message of another room and expected 200
with the text. It expects 403 now, and it asserts that the text is absent.
Commit `9be6e705`.

### Mutations, each measured

| Task | Mutation | Measured result |
|---|---|---|
| 3 | Drop the `kind` write | The four Cassandra pointer round trips failed alone |
| 7 | Permit every read (`permitAll`) | The REST deny, the RSocket outsider read and the expression pin failed |
| 8 | Drop the `FORWARD` check | Only its own creation cases failed |
| 8 | Drop the target read check | Only its own creation cases failed |
| 8 | Admit a pointer to a pointer | Only its own creation cases failed |
| 8 | Read the registry instead of the topic store | Only its own creation cases failed |
| 10 | Drop the pointer branch of the MCP decoder | Only the MCP decoder test failed |

### The gates

Measured on 2026-10-10 at the branch tip, before the documents commit.

- Default mode: exit 0, 30 modules, 2104 tests, 0 failures, 0 errors, 47
  skipped, and no drift.
- `--ci` mode: exit 0, 30 modules, 2486 tests, 0 failures, 0 errors, 82
  skipped, no drift, and `agent http gate: ok`. The run used an empty
  `DOCKER_CONFIG`.
- The shell test image is `sha256:f90664f4`. Its `LastTagTime` is
  `2026-10-11 03:23:57 UTC`, which is inside the run window, so the run
  rebuilt it. **The id before the run was not captured**, so this reading does
  not prove a change of id. It proves a rebuild.
- The agent HTTP gate ran three classes with 0 skipped:
  `RestAgentSelectionTests` 2, `McpMessagingDeploymentTests` 6, and
  `RestToCoreBearerDeploymentTests` 7.
- `docker events` reported no OOM kill in the run window.

**`McpMessagingDeploymentTests` runs only under `expose-webflux`**, so the
reactor skips all six of its tests. That is why the `--ci` skip count is 82
and not 76.

### Risks that stay open

- **The routes, the index and the shell have no pointer test.** A defect there
  reaches `master` unseen. The owner accepted this on 2026-10-10.
- **The Redis and Kafka pub/sub codecs have no pointer test.** A pointer that
  does not serialize there fails `U` after `P` stores it. The room history
  still shows it.
- **A reader that ignores the type shows a pointer as an empty message.** The
  pointer `data` is `""`. The shell, REST and MCP readers of this repository
  each branch on `PointerMessage`. An outside client may not.
- **The `messageById` change refuses a reader that worked before.** A client
  that reads a message by id from a room it did not join answers 403.

### The removed pointer row (2026-10-11)

The review of PR #213 found one defect. **No read of a live pointer changed.**

`MessagePersistenceCassandra.message` rebuilt every pointer with a live flag.
The Cassandra removal keeps the row and clears the flag, so a removed pointer
read back as a live record. `MessagingServiceImpl.pointerTarget` filters on
`record`, so its filter could not reject that row. The call then read a live
pointer and refused it with `a pointer cannot target a pointer` rather than
`no such message`.

`PointerMessage` carries `record` as a constructor field now, with a default of
`true`. The default keeps every existing construction site and the pinned wire
JSON unchanged. **Cassandra is the only backend with a soft removal**, so it is
the only backend that read a removed pointer back.

Three readings carry the evidence.

- `PointerStoreTestBase` gains `a removed pointer does not read back as a live
  record`. It runs on the memory, Redis, Cassandra long and Cassandra uuid
  stores.
- `CassandraMessagePointerTests` is new, at node id 29. It measures the refusal
  text through the composite service on a Cassandra deployment.
- **The mutation, run alone, reproduces the review text.** A mapper that drops
  `row.record` fails the store test in both Cassandra key types, and fails the
  composite test at its refusal assertion with the measured text
  `a pointer cannot target a pointer`.

**The composite test was reordered for that reading.** Its flag assertion ran
before the refusal, so the mutation tripped the flag and masked the consequence
the reviewer named. The refusal is asserted first now.

Both gates pass on the repaired tree. Default mode: 30 modules, 2105 tests, 0
failures, 0 errors, 47 skipped. `--ci` mode: 30 modules, 2491 tests, 0 failures,
0 errors, 82 skipped, `agent http gate: ok`, and no drift.

### The flag crosses the JSON boundary (2026-10-11)

A second review found the same flag lost at the wire. **The store fix alone did
not close the defect for a client.**

Both pointer deserializers built `PointerMessage(key, target)` and took the
constructor default of `true`. The sibling `MessageDeserializer` and
`Jackson3MessageDeserializer` already read `node.get("record")`, and the two
pointer ones did not. So a Cassandra read returned a removed pointer with
`record = false`, and a client that decoded that answer saw a live pointer.

Both deserializers read the flag now. **The serializer side needed no change**,
and the wire tests prove it: the flag is on the wire, and both `true` and
`false` survive a round trip.

`PointerWireShapeTests` is new, with six cases. Two decode a removed pointer,
two decode a live one, and two assert the wire carries the flag. **Both
generations need their own reading**, because each one holds its own pointer
deserializer. Its Jackson 2 mapper is built from `Jackson2ObjectMapperBuilder`,
as `Jackson2MapperConfiguration` builds the deployment mapper. **A plain
`ObjectMapper` cannot serialize a `MessageKey`**, because the timestamp needs
the JavaTime module that builder installs.

Four construction sites exist in main source, and each is now sound. Two
deserializers read the wire flag, the Cassandra mapper reads `row.record`, and
`MemoryDomainCommandBus` makes a new pointer, which is live by definition.

The mutation, run alone, fails exactly the two removed-pointer cases, in both
generations.

Both gates pass again. Default mode: 30 modules, 2111 tests, 0 failures, 0
errors, 47 skipped. `--ci` mode: 30 modules, 2497 tests, 0 failures, 0 errors,
82 skipped, `agent http gate: ok`, and no drift.

**The `Checkout` row of this file is not moved by this section.** See the rule
under `Where things stand`. **The refresh that carries this section moves the
row**, and it reads `dc116ea7`, the last substantive merge before the refresh.

## Merges from PR #210 to PR #213 (2026-10-10/11)

Each merge was measured on 2026-10-11 with `git rev-list --parents` and
`git diff <tip> <merge>`.

| PR | Merge | Tip | Issue | What |
|----|-------|-----|-------|------|
| #210 | `13a3e56b` | `13a3e56b` | `CHAT-tatbvwjo` | Refresh the forward register after PR #209 |
| #211 | `f6639abc` | `17d388de` | `CHAT-flhgiule` | Remove the unused topic access root keys |
| #212 | `3e8d8b03` | `30a54db7` | `CHAT-ircckvrs` | Wait for user writes before returning the key |
| #213 | `dc116ea7` | `5c24e6e9` | `CHAT-erkoxybm` | Message pointer: a message that names a message or a topic |

Three merge trees equal their tips. **PR #210 has no tip of its own**, and the
reason follows.

### PR #210 landed without a merge commit

**`13a3e56b` has one parent, `69dbd2ee`.** The merge commit and the pull
request head share one sha, so the branch landed by fast-forward. That is not
a merge commit.

The repository takes merge commits only since 2026-09-17. Squash and rebase
are both disabled. So this tip is worth questioning, and it is recorded rather
than corrected. **Nothing is lost.** The tree that `13a3e56b` holds is the tree
the refresh commit wrote, and that content is on `master`.

**PRs #211, #212 and #213 each carry two parents.** So the rule holds for
every merge after #210.

### The stale issue that the sprint close found

**`CHAT-flhgiule` was `in-progress` while its work was merged.** PR #211
merged it as `f6639abc`, and the pull request body names the issue. The status
was never moved. It is `done` now, closed on 2026-10-11.

**This is the second occurrence of one class.** `CHAT-ubmrxyqo` carries the
first, recorded under `Housekeeping`. A merged issue that keeps an active
status makes the tracker lie about the sprint.

### The scheduled dependency audit is red, and the owner deferred it

**Five consecutive runs have failed**, and each one names the same finding.

| Run | Date |
|---|---|
| `37615558346` | 2026-10-07 |
| `37773162122` | 2026-10-08 |
| `37925766480` | 2026-10-09 |
| `38047078329` | 2026-10-10 |
| `38135195213` | 2026-10-11 |

Every failure reads one line:

    kotlin-sdk-server-jvm-0.15.0.jar
      (pkg:maven/io.modelcontextprotocol/kotlin-sdk-server-jvm@0.15.0,
       cpe:2.3:a:jetbrains:kotlin:0.15.0:*:*:*:*:*:*:*)
      : CVE-2026-53914(9.8)

**The CPE is probably wrong.** JetBrains Kotlin never published a 0.15.0
release. The run of 2026-10-07 names five older Kotlin CVEs on the same CPE,
such as CVE-2019-10101. Those CVEs describe the Kotlin language, and not this
adapter.

**The caution is the suppression.** CVE-2026-53914 is also recorded against
`kotlin-stdlib` 2.4.10, which this repository holds. So a suppression must
name the MCP SDK `packageUrl` alone. A suppression by CVE would hide a real
finding.

**The owner deferred the repair on 2026-10-11.** `CHAT-wgiwqodp` holds it.
The workflow runs on a schedule, so it never gates a pull request. **The three
pull-request jobs are green.**

### The pull-request workflow is green

Run `38126053450` tested the PR #213 head `5c24e6e9`. All three jobs passed:
`build` in 8m48s, `integration` in 17m4s, and `agent-http` in 2m32s.

**This is the first sprint close in this file with three green pull-request
jobs.** Every section from 2026-10-01 to 2026-10-03 records a red integration
job, under B12.

### Open after these merges

- **`CHAT-aysfqhnm` holds this refresh.** It follows PR #213.
- **`CHAT-mpfuifei` is done.** PR #215 merged on 2026-10-12 as `48e861ad`, a
  merge commit with two parents, `e8ece7fd` and `f414b0ff`. The branch is gone,
  local and remote, and no worktree remains. **This file recorded it as `todo`
  from 2026-10-11 until this correction.** That reading came from the file
  alone, and the issue had merged.
- **`CHAT-wgiwqodp` holds the red scheduled audit.** The owner deferred it.
- **`CHAT-pggtduxz` closed on 2026-10-11.** PR #208 merged its work, and the
  owner closed the issue.
- **Two live branches carry worktrees**, `chat-emwhnlay-import` and
  `chat-lswjobhz-shared-store-index`. Neither is part of this sprint.

## Lucene identity fields (CHAT-hajmhslp, 2026-10-13)

The `chat-hajmhslp-index-exact` branch changes Lucene identity fields to
whole-value fields. An `_x_` field name selects `StringField` and `TermQuery`.
Message `data` keeps text analysis. `IndexFieldKinds` owns the field rules and
the NFC normalization used by both index writes and lookups. User handles also
use `Locale.ROOT` lowercase. Topic names keep case.

Entry format 2 makes a file index with format 1 rebuild at its next start.
The request type is unchanged, and its `first` field carries the field name.
**An exact query must send the `_x_` prefix in that field.** `IndexFieldKinds`
refuses a plain name for an exact field and names the prefixed form.
**No alias period exists**, because the refusal is a `ChatException`.
The key-value field registration is unchanged.
The membership index uses one exact field for each member-room pair.

`CHAT-zxrgxdlx` still depends on this issue. It sets the imported message id to
`nameUUID("<topicId>:<messageUuid>")`. The operator supplies a generation in
the topic name only. The message gains no `source_id` or wire change. A
room-specific JSON source map lives beside the export. An incompatible long
key type must fail at startup. The importer remains separate work.

## Cassandra contact points (CHAT-fmvsylrv, 2026-10-14)

PR #225 merged on 2026-10-14 as `cc934030`. The merge has two parents,
`6a9b8b55` and `3e1c5682`, so it is a real merge commit. The merge tree is
byte-identical to the branch tip `3e1c5682`.

A Cassandra deployment carried a second contact point.
`ContactPointConfiguration` called `sessionBuilder.addContactPoint`, and it
never removed the point that `AbstractSessionConfiguration.getContactPoints()`
supplies, which is `localhost` with port `9042`. `SessionBuilder` has no clear
method.

**The harm appeared on this host, and one test found it.** A docker compose
stack of another session published `127.0.0.1:9042` and held the `chat_long`
keyspace. `CassandraClaimBootTests` seeded node 21 in the test container and
then booted the application. The claim store read the foreign cluster, which
held no node 21 row, so the claim was granted and `assertThrows` found no
exception. That is why the `--ci` gate of the `CHAT-hajmhslp` branch reported
`chat-deploy-cassandra` as a new failing module.

**The risk was wider than the test.** Any Cassandra deployment could bind to a
foreign cluster that answers on `localhost:9042` and holds the same keyspace
names. Reads and writes would then reach the wrong store.

`ContactPointConfiguration` overrides `getContactPoints()` and `getPort()` with
the configured values now. The session builder applies credentials alone.
`ContactPointConfigurationTests` pins both. `docs/BUILD-HEALTH.md` records the
failure signature and the check for a foreign listener.

`CHAT-axkakbhl` added the contact point fallback that caused this. The module
had this defect from that change until PR #225.

One limit stays open. No deployment ran against a remote cluster. Every reading
came from a test JVM.

## The Claude import topic scope (CHAT-emwhnlay, 2026-10-14)

`CHAT-emwhnlay`. Branch `chat-emwhnlay-import`, in the worktree
`/Users/darkbit1001/.codex/worktrees/f019/demo-chat`. **This work is not
merged.** The reviewed work order is
`docs/superpowers/specs/2026-10-13-claude-thread-topic-import-work-order.md`.

### The collision, and the fix

**One export record reaches two topics.** Rule 4 of the work order puts a
shared parent into every thread that uses it. A fork therefore imports one
record into two rooms.

**The route refused the second room.** `MemoryDomainCommandBus.admit` keys a
request on the owner and the request ID. Its fingerprint includes the room. A
repeat with a different room is a `RequestConflictException`, not a duplicate.
So one request ID names one message.

**`MessageImportRequest.messageId` has one consumer**, which is
`requestId = "import:${req.messageId}"` at `MessagingServiceImpl.kt:184`. The
route treats the value as opaque, so the caller owns its scope.

**The source ID is `<topicId>:<messageUuid>` since 2026-10-14.** It was
`<conversationUuid>:<messageUuid>`. The value is also the derivation input
that `CHAT-zxrgxdlx` names, so one spelling serves both.

### The evidence

- `ClaudeImportCommandsTests` 15 of 15. The new case `a shared root names one
  source ID per topic` failed at `:286` before the fix.
- `ClaudeImportDeploymentTests` 4 of 4 on the rebuilt image. The new case
  `a forked conversation imports each leaf into its own topic` covers rule 4
  on the real route. The second generation case failed with
  `RequestConflictException` before the fix.
- `build-health.sh --ci`: exit 0, 30 modules, 2548 tests, 0 failures,
  0 errors, 82 skipped, `agent http gate: ok`, and no drift. The shell image
  id moved from `f6d43011d980` to `9b02c7070e02`. `docker events` reported no
  OOM kill in the run window.
- Two unit tests cover acceptance rows that no test held before:
  `an added leaf renames no existing topic`, and `the largest room id fits
  the request limit`.

### The sentence the measurement refuted

**The work order said the source ID stays conversation-scoped.** That sentence
contradicted its own rule 4. It is corrected in place, and the design
document's corrections section records it.

### The owner settled the boundary on 2026-10-14

**The room-scoped source ID stays in the importer.** PR #226 keeps it. The
route does not change in that pull request.

**A route-scoped request ID remains defensible, and it waits.** The route owns
the request identity for every caller. A change there would move the identity
of imports that already exist. `CHAT-zxrgxdlx` settles that general contract
before it derives the message keys. It is a child of `CHAT-emwhnlay`.

**The owner measured the callers.** The shell is the only production caller.
The route tests use unscoped IDs, so they do not pin the contract either way.
The issue comment of `CHAT-zxrgxdlx` records both options now.

**`CHAT-emwhnlay` stays open for that child work.** The long-key startup
refusal and the derived-ID read are open with it.

### Open

- **PR #226 merged on 2026-10-15 as `08a71e81`.** Its CI run `38664537134`
  passed all three jobs: `build`, `integration`, and `agent-http`. The branch
  ref is gone, local and remote, and the worktree is removed.
- `CHAT-zxrgxdlx` owns the derived message key, the UUID-only import gate, and
  the source-to-chat map. It is `todo` and holds no code.
- The `Checkout` row of this file is not moved by this section.

## Merges from PR #214 to PR #228 (2026-10-12/15)

Each merge was measured on 2026-10-15 with `git rev-list --parents` and
`git diff <tip> <merge>`. **Every merge has two parents, and every merge tree
equals its branch tip.** So each one is a real merge commit, and none carried a
conflict repair.

| PR | Merge | Tip | Issue | What |
|----|-------|-----|-------|------|
| #214 | `e8ece7fd` | `edead755` | `CHAT-aysfqhnm` | Refresh the forward register after PR #213 |
| #215 | `48e861ad` | `f414b0ff` | `CHAT-mpfuifei` | Cassandra `topic byIds` |
| #216 | `d89809d7` | `81dd9100` | `CHAT-ivtnvgyg` | Name the half that fails in the key exists test |
| #217 | `baf11a90` | `e9fa6973` | `CHAT-liomkoer` | Give the key removal test a route and an assertion |
| #218 | `0552c532` | `c2b5fe5d` | `CHAT-ivtnvgyg` | Read the reach of the key read in two steps |
| #219 | `20a1df99` | `ed20052b` | `CHAT-dbltvvmw` | Deploy a Cassandra core with Lucene and Redis |
| #220 | `b8db69f7` | `480fd02f` | `CHAT-dbltvvmw` | Add authorization server Just recipes |
| #221 | `4ff78cc8` | `24201c92` | `CHAT-dbltvvmw` | Deploy the authorization server in Docker |
| #223 | `27ab4772` | `adb9efcd` | `CHAT-dbltvvmw` | Refresh the core launch goldens |
| #222 | `78bf538d` | `1c615226` | `CHAT-ybmlcdiw` | Close the shell context so the JVM exits |
| #224 | `6a9b8b55` | `256e1b41` | `CHAT-hajmhslp` | Query the Lucene identity fields through the shared builder |
| #225 | `cc934030` | `3e1c5682` | `CHAT-fmvsylrv` | Use the configured Cassandra contact points |
| #226 | `08a71e81` | `77e544ae` | `CHAT-emwhnlay` | Import every Claude conversation thread into its own topic |
| #227 | `b26704bf` | `24f10159` | `CHAT-nxbtadan` | Split the register into a live file and three period logs, with a size check |
| #228 | `04dbe77a` | `c4ee22f6` | `CHAT-ivtnvgyg` | Bind the RSocket test server to loopback, and correct the trap entry |

**PR #223 merged before PR #222, and the rows show it.** The first parent of
`78bf538d` is `27ab4772`, the merge commit of #223. So a pull request number is
not a merge order.

**Three merges in this range are branch syncs, not pull requests.** `1c615226`,
`709ad7d0` and `94957eb7` each merge `master` into a live branch. `94957eb7` is
the last one, and it brought `master` to the tip of #226 before that merge.

**`CHAT-ybmlcdiw` is done.** PR #222 closes the shell context, so the JVM
exits. The issue holds the measured cause: the web server thread held the
actuator port, and `spring.shell.context.close` now defaults true for the
shell launch.

**`CHAT-dbltvvmw` is done**, and it carries four of these merges: #219, #220,
#221 and #223.

## The register split (CHAT-nxbtadan, 2026-10-15)

`CHAT-nxbtadan`. Branch `chat-nxbtadan-register-64kb`, removed after the
merge. **PR #227 merged on 2026-10-15 as `b26704bf`**, a merge commit with two
parents, `08a71e81` and `24f10159`. The merge tree equals the branch tip.
`docs/BUILD-HEALTH.md` holds the check, under
`The forward register size check`.

### The problem

**`forward-register.md` was 280,889 bytes, and `AGENTS.md` imports it.** So the
file loaded in every session, and its size was a cost on every turn. The owner
set a limit of 64 KB on 2026-10-15.

### The split

- Every section dated before 2026-10-08 moved to a period log, verbatim:
  `forward-register-archive-2026-08.md` with 4 sections,
  `forward-register-archive-2026-09.md` with 41, and
  `forward-register-archive-2026-10.md` with 25.
- The live file keeps the durable core and every section dated on or after
  2026-10-08. It holds 23 sections: 14 dated ones, the eight durable blocks,
  and `## The archive`.
- **A period closes when the live file would pass 64 KB.**
- `build-health.sh` measures the file in every mode. It fails above 65,536
  bytes and prints `REGISTER TOO LARGE`.

### What the split corrected

- **The preamble described the 2026-08-23/24 handoff**, and it was replaced.
- **One pointer was dangling.** `app.nodeid` pointed at a lease section
  "below", and that section moved to the 2026-08 log. The pointer names the
  file now.
- **Two state facts were stale.** The state table read 2026-10-11, and the
  merge record stopped at PR #213. This file holds a table for PRs #214 to
  #226 now.

### The proof

- **Each of the 90 `## ` headings of the source appears exactly once** across
  the four files.
- **The split outputs held the source byte count exactly**: 280,889 bytes, over
  the preamble and the three logs.
- **Three bodies changed, and each is intended.** The state table is a
  rewrite. `Things worth not relearning` gained the repaired pointer. The
  import section records the merge of PR #226.
- One assembly defect was found and repaired. The 2026-10 log first carried a
  3,352-byte copy of the state table's tail. The rebuilt file is the split
  output alone, under a header.
- **`build-health.sh --ci` exited 1, and `chat-client-rsocket` caused it.**
  `UserClientTests` failed 4 of 5 with `ClosedChannelException` and two 45.01 s
  waits. `chat-authorization-server` and `chat-shell` then reported SKIPPED.
  **This work changes no Kotlin and no pom.** The class passes alone, 5 of 5 in
  2.1 s.
- **The failing context bound port 64342, and IntelliJ held
  `127.0.0.1:64342`.** The client connects to `localhost`, so its frames
  reached IntelliJ. **A first reading exonerated the port trap and it was
  wrong.** It read the ports of other contexts, and missed the failing one.
  Forcing the server port to 64342 fails the class the same way in 137 s.
  **PR #228 repaired the trap on 2026-10-15.** The test server binds loopback
  alone now, and a reader finds the mechanism under
  `Things worth not relearning`.
- **The second `--ci` run passed, on the same tree.** Exit 0, 30 modules, 2548
  tests, 0 failures, 0 errors, 82 skipped, `agent http gate: ok`, and no drift.
  **So the failure above is intermittent**, because the kernel picks the port.
  `CHAT-ivtnvgyg` holds it.
- The register size check printed nothing in either run, because the live file
  is under the limit.
- **CI run `38678368389` passed all three jobs** on the merged head
  `24f10159`: `build` 8m30s, `agent-http` 3m1s, and `integration` 17m0s. The
  integration job ran `UserClientTests` 5 of 5 in 0.466 s, and it held no
  `ClosedChannelException`.
- **The trap is local, and the Jackson 3 failure is not.** The trap needs an
  IntelliJ listener on the same machine, so a CI runner cannot meet it.
  `CHAT-ivtnvgyg` stays in progress for the codec error on `KeyTests`, and
  PR #228 does not touch that path.
