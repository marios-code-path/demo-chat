# The MCP agent identity

Issue `CHAT-werokcbb`, under the closed parent `CHAT-ylvoiixm`.

**Status: approved for planning.** The owner approved this specification on
2026-10-01 and recorded two decisions. Nothing in this document is implemented.

Measured on 2026-10-01 at master `c069c978`.

## The gap

The adapter credential resolves to the `Admin` account. So the adapter carries
administrator rights.

`AgentIdentityLifecycle.start` resolves one chat user from
`app.security.agent.username`. It reads
`users.findByUsername(ByStringRequest(username))`, and it throws unless the
answer holds exactly one user. The measured issuance procedure names `Admin`,
because startup creates that account.

No path creates a dedicated agent user today. `InitialUsersService` reads
`initialUsers` from `userinit.yml`, and that list holds `Anon` and `Admin`. It
refuses a third entry with
`An initial user names an unknown identity: <name>`.

So the agent identity and the administrator identity are one account. An agent
token reaches everything the administrator reaches.

## The finding that reshapes this issue

**The narrow grant this issue asks for already exists.** No grant row is needed.

The adapter makes one backend call. `TopicClient.readTopic` sends
`GET <base>/topic/id/<id>`. That route is
`ChatTopicServiceRestMapping.restGetRoom`, and it carries
`@PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'GET')")`.

Three measured facts make that read allow for any identity.

1. The check reads the given target and the domain root of that target.
   `CoreAuthorizationService.targets` returns both. `CHAT-rfzsnbco` added that.
2. The shipped row `{user: User, target: MessageTopic, role: GET}` names the
   `MessageTopic` domain root as its target. So it reaches a check on one room.
3. Every query's actor set holds the `Anon` key and the `User` root.
   `CoreAuthorizationService.actors` states both. So that row reaches every
   caller that holds an identity.

So the adapter reads a room today with the shipped configuration alone.

**Therefore `RootKeys.byName` never has to resolve an agent name.** That
removes the reason for the change this issue assumed. `ChatIdentity` stays
closed. `RootKeys` and `RootKeySnapshot` stay as they are, and the wire shape of
a root key snapshot does not move.

**The work is therefore one thing.** Create a dedicated agent user, and point
`app.security.agent.username` at it. The narrowness is a loss, not a gain. The
adapter stops holding `Admin`'s `*` on every domain root.

## The decisions of the owner, 2026-10-01

1. **The agent is a separate initial user.** `userinit.yml` declares it, beside
   `Anon` and `Admin`. Every deployment that loads the shipped file creates it.
   The alternative, an operator script that creates the user after boot, cannot
   serve the single-process memory shape. That shape holds the store itself, so
   no earlier process exists to create the user.
2. **A blank password generates a random password.** The rule applies to every
   initial user. The service writes the generated password to the console. The
   owner accepted the consequence for the `Admin` account, which is that an
   operator who blanks the admin password prints a live administrator
   credential.

## What changes

Three source files change.

| File | Change |
|---|---|
| `shared-deploy-configuration/src/main/config/userinit.yml` | One new `initialUsers` entry, `Agent`. It carries `handle`, `name` and `imageUri`. It carries no `password` key. |
| `chat-deploy/.../init/UserInitializationProperties.kt` | `UserDefinition.password` takes a default of `""`, so the key may be absent. |
| `chat-deploy/.../init/InitialUsersService.kt` | Two changes. See below. |

Four documents change.
`docs/MCP-CREDENTIAL-ISSUANCE.md` names `Agent` and closes limit 1.
`docs/BUILD.md` names `Agent` in its launch rows.
`docs/ANONYMOUS-AUTHORIZATION.md` records the agent identity and risk 4.
`forward-register.md` records the change.

### `InitialUsersService.loadIdentities` relaxes

The method requires `Admin` and `Anon` by name today, and it refuses every
other initial user. It changes to three rules.

1. `Admin` must be present. Its key loads as the admin identity.
2. `Anon` must be present. Its key loads as the anonymous identity.
3. Any other initial user becomes a plain user. It does not enter `RootKeys`.

**A typo still fails the start.** A mistyped `Admin` key leaves the
`Admin` identity missing, and rule 1 refuses it.

### `InitialUsersService.initializeUsers` generates a password

The method reads `thisUser.password` and encodes it. It changes to two rules.

1. A blank password generates a random password. `SecureRandom` supplies the
   bytes.
2. The method writes the account name and the generated password to the
   console.

**The generated value is written again at every start.** `initializeUsers` runs
on each start, and `secretsStore.addCredential` overwrites the stored
credential. So only the newest startup output holds the live password.

**A role row cannot name the agent.** The role loop resolves each name through
`RootKeys.byName`. `Agent` is not a domain and not a root identity, so the
lookup answers null and the loop prints
`Missing root key for Agent or <target>`. This is deliberate. No role row names
the agent, and the agent needs none.

## What does not change, and why

- **`ChatIdentity` stays closed.** It names the two bootstrap root identities.
  `RootKeys.identity`, `admin` and `anon` keep their meaning. The agent is a
  plain user, so it needs no entry.
- **`RootKeys` does not change.** `loadIdentities` keeps two parameters.
- **`RootKeySnapshot` does not change.** No process reads an agent key from a
  snapshot, because only the grant write needs a name to key mapping, and the
  agent needs no grant.
- **`AgentIdentityLifecycle` does not change.** It resolves by handle alone.
- **No grant row is added to `userinit.yml`.** The shipped ten rows stand.

## What the agent may do

The agent holds the floor that every identity holds. The floor is the `Anon`
key, the `User` root and the caller, and the shipped rows that those names
reach.

| Operation | Agent | Admin |
|---|---|---|
| `getRoom`, `GET` | allow | allow |
| `listRooms`, `GET_ALL` | allow | allow |
| `addRoom`, `NEW` | allow | allow |
| `whoami`, `User FIND` | allow | allow |
| `messageById`, `Message GET` | allow | allow |
| `addUser`, `User NEW` | **deny** | allow |
| `deleteRoom`, `REM` | **deny** | allow |
| `send` to a room the caller does not own | **deny** | allow |

**The `Admin` rows do not reach the agent.** `InitialUsersService` writes one
`*` row per loaded domain root for the admin key. That key is in no other
caller's actor set, so those rows reach the admin identity alone.

### The scope ceiling, stated plainly

**No grant change can narrow one caller below the floor.** `actors` supplies
the `Anon` key and the `User` root to every query. Nothing in this application
subtracts a permission. So "narrow" here means "no administrator reach". It
does not mean "the topic read alone".

The agent therefore also holds `addRoom`, `listRooms` and `messageById`. Each
of those reaches every identity today, and each is wider than the adapter
needs. Narrowing them is a separate decision about the shipped grants. It is
not part of this issue.

## Verifying the agent at startup

`AgentIdentityLifecycle.start` requires exactly one user for
`app.security.agent.username`. Two facts follow, and both are load-bearing.

1. **A user created after startup cannot satisfy agent resolution.** The
   lifecycle runs once, before the reactive web server starts. So an operator
   script that creates the user later leaves the start failed, not delayed.
2. **The deployment fails loudly on a wrong name.** Zero matches and two
   matches both throw, and the message names the handle and the count.

So the declared initial user is the route. It exists before the lifecycle runs
in every composition.

## Verification

| Gate | What it proves |
|---|---|
| `AnonymousAuthorizationMatrixTests`, a new administrative caller | The admin row set differs from the agent row set at `addUser` and `deleteRoom`. The narrowness is real and measured. |
| A new `chat-deploy-memory` test against the shipped `userinit.yml` | The `Agent` user exists, and its key differs from the admin key. |
| A new `InitialUsersService` test | A blank password generates one, and the console carries it. An explicit password prints nothing. |
| A new `InitialUsersService` test | A third initial user loads as a plain user. A missing `Admin` key still fails the start. |
| The packaged acceptance run | The appendix flags of `docs/MCP-CREDENTIAL-ISSUANCE.md` with `--app.security.agent.username=Agent` answer 200 for an allowed topic, and 401 for a junk credential. |
| The default gate and the `--ci` gate | `shell-scripts/build-health.sh` reports no drift. |
| `drift check` and `git diff --check` | They pass for every bound document. |

The reactor runs in full. A scoped `-pl` run resolves upstream modules from
`~/.m2` and reports failures that are not real.

## Risks

1. **Every deployment that loads the shipped file creates the agent user.** The
   account holds no grant of its own. It holds the floor, as every identity
   does. A deployment that wants no agent account overrides `userinit.yml`.
2. **The generated password reaches the console.** The owner accepted this. The
   line prints once per account per start, and it names the account.
3. **The agent needs a store that survives a start in the facade shape.** A
   facade resolves the agent at its own start, so the user must already exist in
   the shared store. The core deployment creates it. That is the same rule that
   the root key source already follows.
4. **The adapter's read rests on a floor row, not on an agent row.** A later
   change to `{user: User, target: MessageTopic, role: GET}` would remove the
   adapter's read and no agent-scoped row would exist to restore it. The
   `send` rule is the same shape. Record this in
   `docs/ANONYMOUS-AUTHORIZATION.md`.

## Out of scope

- **The `core` access interfaces.** They stay latent. `CHAT-ruapxetl` holds the
  programmatic wrappers.
- **The `send` rule.** The owner decided nothing about a join that writes a send
  grant. Nothing implements it.
- **A second agent account, or a per-agent grant.** One agent identity serves
  one adapter. A second adapter is a separate decision.
- **`chat-shell`.** `CHAT-wbcbptiq` and `CHAT-dgjhljbl` hold its two failure
  sets.
