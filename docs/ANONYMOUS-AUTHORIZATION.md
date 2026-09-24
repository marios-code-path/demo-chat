# The anonymous authorization matrix

What a caller may do, and what the `Anon` root key may do.

**Measured on 2026-09-24 at `58163b7c`.** The first measurement was taken on
2026-09-23 at master `4fcae69c`, and one row has moved since. `listRooms` went
from deny to allow under `CHAT-mahevldm`.

`docs/IDENTITY-POLICY.md` states which identity a caller reaches. **This
document states what that identity may then do.** They are separate questions,
and a green identity test proves nothing here.

**This matrix describes a store that keeps every grant row.** The test
replaces the store and the index with maps. Measured on 2026-09-24, the
cassandra authorization index keeps one row per target and one row per
principal, so it cannot hold the four `User` root rows that `userinit.yml`
writes. **Do not read this matrix as the answer for a cassandra deployment.**
`CHAT-rmxxtwtu` holds that defect.

**`*` means ownership, and not "all permissions".** It is singular per target,
and it is a sentinel, so a `*` row stops the read and its expiry decides.
`docs/superpowers/specs/2026-09-23-operation-policy-draft.md` states the three
properties under `What \* means`. **No shipped row names `*` on a target that
an operation below checks**, so the rank does not move this matrix. The one
shipped `*` row names the `Admin` key as its target, and no operation here
names that target.

`AnonymousAuthorizationMatrixTests` holds the measurement. It wires the
production `CoreAuthorizationService`, `AuthSummarizer`,
`AuthMetadataAccessBroker` and `SpringSecurityAccessBrokerService`, and
replaces only the store and the index.

## The grants

Every deployment loads
`shared-deploy-configuration/src/main/config/userinit.yml`. It holds nine
roles. **All nine are listed here, because since `CHAT-mahevldm` the four that
name `User` reach a caller.** An earlier version of this section listed the
three `Anon` rows alone.

| Principal | Target | Permission | Reaches a caller |
|---|---|---|---|
| `Admin` | `Admin` | `*` | only a caller holding the `Admin` key |
| `Anon` | `User` | FIND | yes |
| `Anon` | `User` | PUT | yes |
| `Anon` | `Message` | GET | yes, and no operation matches its target |
| `User` | `Message` | SEND | yes, and no operation matches its target |
| `User` | `MessageTopic` | ALL | yes |
| `User` | `MessageTopic` | GET | yes, and no operation matches its target |
| `User` | `MessageTopic` | JOIN | yes, and no operation matches its target |
| `User` | `MessageTopic` | MEMBERS | yes, and no operation matches its target |

"Reaches a caller" is the principal side. "No operation matches its target"
means the row names a domain root while every operation that asks for that
permission names one object. Result 3 below carries that.

## The matrix

The operations are the `@PreAuthorize` expressions of the access interfaces in
`chat-security`.

| Operation | anonymous | authenticated | unauthenticated | unsupported | no context |
|---|---|---|---|---|---|
| `addRoom`, MessageTopic NEW | deny | deny | deny | deny | deny |
| `send`, room SEND | deny | deny | deny | deny | deny |
| `whoami`, User FIND | **allow** | **allow** | deny | deny | deny |
| `messageById`, GET | deny | deny | deny | deny | deny |
| `listRooms`, MessageTopic ALL | **allow** | **allow** | deny | deny | deny |
| `addUser`, User NEW | deny | deny | deny | deny | deny |

## Three results that are easy to miss

1. **An anonymous grant is a floor for every caller, and so is a `User` root
   grant.** `CoreAuthorizationService` puts the `Anon` key **and the `User`
   root** in the actor set of every query. So an authenticated caller reaches
   the same answers as an anonymous one, plus whatever names its own key.

   The `User` root half arrived with `CHAT-mahevldm` on 2026-09-24. Before it
   the `Anon` key was the only floor.
2. ~~**The four `user: User` rows reach nobody.**~~ **They reach every caller
   since `CHAT-mahevldm`, measured on 2026-09-24.** The actor set carries the
   `User` root beside the anonymous key, because every caller is a user.

   **`listRooms` moved from deny to allow** for an anonymous caller and for an
   authenticated one. That is the only row of this matrix that moved, and the
   shipped configuration is what says so: `{user: User, target: MessageTopic,
   role: ALL}`.

   `GET`, `JOIN` and `MEMBERS` still reach no operation, because each names the
   `MessageTopic` root as its target and every operation that asks for them
   names one room. Result 3 explains that.
3. **A grant on a domain root does not cover one object.** This is the target
   side, and it is still open. `CHAT-rfzsnbco` carries it. It applies to
   the nine checks that name an object key. `Anon` holds `Message:GET`, and
   `messageById` checks `hasAccessTo(<one message key>, 'GET')`. The grant
   names the Message root key, so it never applies. `send` is the same shape
   against a room key.

   **Five checks already name a root as the target.** `hasAccessToDomain`
   passes `rootKeys.getRootKey(domain)`, so `addRoom`, `listRooms`, `addUser`,
   `findByUsername` and `findByUserId` compare against the domain root
   directly. At those five the target side already matches a `target:
   <Domain>` row. **The principal side matched too, since `CHAT-mahevldm`.**
   That is why `Anon:User:FIND` allows `whoami`, and why
   `User:MessageTopic:ALL` now allows `listRooms`.

So the shipped configuration allows three permissions to every caller that
reaches an identity: `User:FIND`, `User:PUT` and `MessageTopic:ALL`. **Every
write operation denies for every caller.**

`addRoom` and `addUser` deny although both sides now match, because no shipped
row grants `NEW` on either domain.

## Expiry

`AuthSummarizer` keeps a row when `expires` is `0L` or in the future. A grant
with an expiry in the past does not allow. That is the only expiry this
application has. **No credential expires, because no deployed transport
validates a token.** See `docs/IDENTITY-POLICY.md`.

## What is not wired

**No production type implements the annotated interfaces.**
`TopicServiceAccess`, `UserServiceAccess` and `MessageServiceAccess` in
`com.demo.chat.security.access.composite` carry every `@PreAuthorize` in this
repository. `CompositeControllersConfiguration` imports all three and
implements none. The controllers delegate to `CompositeServiceBeans`, which
supplies the plain services.

The other path, the programmatic wrappers in `chat-service-composite`, needs
`app.service.composite.security`. No launch script, no yml and no test sets
that property.

**So no authorization check runs in any deployed composition today.** The
matrix above is what the configuration means, not what a running deployment
enforces. `CHAT-znprrzhn` holds the wiring gap. `CHAT-ruapxetl` holds a second
defect in the programmatic wrappers.

This also explains why `chat-shell` can create a room and send a message with
no credential. The matrix denies both.

## Before turning the checks on

Read this table first. **Enabling the checks against the shipped grants would
deny `addRoom` and `send` to every caller**, including an authenticated one.
The grants need a decision before the wiring does.

`listRooms` allows since `CHAT-mahevldm`, measured on 2026-09-24. An earlier
version of this line named it beside `addRoom` and `send`, and that was true
while the four `user: User` rows reached nobody.
