# The operation policy, a draft

**This is a design input. No code loads it.** Written on 2026-09-23 from the
owner draft that was written into
`shared-deploy-configuration/src/main/config/userinit.yml`. That file is
configuration, it must parse, and it must match the schema that binds it, so
the draft lives here instead.

`CHAT-zhjltbky` holds the policy decision. `CHAT-zcxgrtqc` holds the typed
schema this draft needs.

Read `docs/ANONYMOUS-AUTHORIZATION.md` first. It measures what the current
grants allow.

## The stipulation

**Everything is denied implicitly.** The permissions in `userinit.yml` cover a
small prototype slice of the known operation surface.

The finer grained policy happens mainly **at creation**. Other sites need
their own decision. `close` is one, because it shuts everyone out except the
holder of `*`.

So the policy applies at command sites.

## The shape

    OperationPolicy { operations: List<Operation> }
    Operation        { authorizations: List<DefaultAuthorization> }
    DefaultAuthorization {
        command: String
        user:    T
        target:  T
        role:    String
        expire:  Enum
    }

An `OperationClass` is a child of `OperationPolicy`, so the minimum
composition is one `OperationPolicy` holding a list of `Operation`.

## The matching rules

The order of matching is:

1. `property_name` or `User`.
2. The match, then `{ROLE}` or `{ID}`.
3. For `ROLE`, the property named must have type `T`. It becomes the
   `principal` side of the AuthMetadata query. The right side of the
   expression is the query on the `role` property.

So `user: dest{ROLE=*}` reads as

    findAccessFor(dest, where role = '*').principal

For `ID`, the value matches one of `ACTIVE`, `ROOT`, `ANON` or `ADMIN`.
`ROOT`, `ANON` and `ADMIN` name the root keys of the User, the anonymous user
and the admin user. `ACTIVE` names the caller that is logged in.

## The proposed entries

`key.id` names the new entity of the command. For `add` on a user, that is the
new user.

### users

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `key.id` | `key.id` | `*` | none | A user holds every right over itself |
| `add` | `User{ID=ANON}` | `key.id` | `-` | none | An anonymous caller cannot see this user |

### messageTopic

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `User{ID=ACTIVE}` | `key.id` | `*` | none | The caller that created the room owns it |
| `add` | `User{ID=ROOT}` | `key.id` | `JOIN` | now | A new room is not joinable |
| `add` | `User{ID=ROOT}` | `key.id` | `MEMBERS` | now | A new room hides its members |
| `close` | `User{ID=ROOT}` | `key.id` | `*` | now | Revoke everyone except the holder of `*` |

### message

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `from` | `key.id` | `REM` | none | The sender may remove the message |
| `add` | `dest{ROLE=*}` | `key.id` | `REM` | none | The owner of the topic may remove the message |

### topicMembership

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `member` | `key.id` | `*` | none | A membership belongs to its member |

## The owner question, recorded and not decided

**Should a policy grant only narrow permissions, or may one user give another
`*` on a topic?**

The owner view in the draft: keep one place where `*` is applied, which is
initialization and system definition. `*` then becomes a stable indicator of
ownership.

That reading makes `dest{ROLE=*}` above meaningful, because it finds the owner
of a topic by looking for the holder of `*`.

## What the current code does not support

Each line is measured on 2026-09-23 at master `42cd6a69`. **Every one of them
must close before any of this reaches configuration.**

1. **`operationPolicy` has no binding type.** `UserInitializationProperties`
   binds `passwordEncoder`, `initialRoles` and `initialUsers`. Spring ignores
   an unknown key, so the expressions would have no effect and no error.
2. **`RoleDefinition` binds `user`, `target` and `role` only.** It has no
   `expire` and no `expires`. The draft uses both spellings.
3. **`*` is not a wildcard.** `AuthMetadataAccessBroker` line 19 reads
   `permissions.contains(perm)`, a literal list check. No main source file
   expands a wildcard. So the existing `Admin` grant and the `*` that
   `TopicCommands.addTopic` writes match no operation.
4. **`rolesAllowed` and `wildcard` bind and are never read.**
   `InitialUsersService` reads `initialRoles.roles` alone.
5. **`role: '-'` has no denial meaning.** The permission check answers whether
   a list contains a string. Nothing subtracts.
6. **A grant on a domain root does not cover the objects of that domain**, and
   **a principal alias does not exist.** `docs/ANONYMOUS-AUTHORIZATION.md`
   measures both.

## What a typed schema must define

`CHAT-zcxgrtqc` carries these.

- The wildcard, and where it may be written.
- Denial, and how it composes with a grant.
- Expiry, including what `none` and `now` mean as values.
- Principal aliases, meaning `ACTIVE`, `ROOT`, `ANON` and `ADMIN`.
- Object grant expansion, meaning whether a domain root reaches its objects.
- Binding tests, then authorization tests, before the policy is enabled.
