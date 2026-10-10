# Store service lease design

Issue: `CHAT-lswjobhz`. Parent: `CHAT-aqpcacwv`. Date: 2026-10-09.

## The problem

Each Lucene index lives in one process. Before this change, two core
processes could serve one shared store, and the node id lease permitted it.

**Gap 1, remote writes.** A write through core A reaches the shared store and
the index of core A. It never reaches the index of core B.

**Gap 2, start scans.** A start compares the store with the index files.
Another process can write the store during that scan. The reused index can
then miss that write.

### Measured on 2026-10-09

`RedisSharedStoreIndexTests` in `chat-deploy-redis`, commit `6ce01bc3`. Node
ids 50 and 51 run on one Redis container with in-memory Lucene indexes.

- A user written through node 50 is in the shared store. Node 51 reads it by
  key. The index of node 51 does not find its handle.
- The duplicate check of `addUser` reads the local index. So node 51 accepted
  the same handle again. The shared store then held two users with one handle.

Gap 2 was not measured separately. Until a remote write reaches another index
at all, a write during a scan is lost like any other remote write.

## The rule

The owner set the rule on 2026-10-09.

**On a shared backend, each store service has exactly one live instance.**

- A store service is one domain variant of persistence or index. The
  persistence services are `UserPersistence`, `TopicPersistence`,
  `MessagePersistence`, `MembershipPersistence`, `AuthMetaPersistence` and
  `KeyValueStore`. The index services are the matching variants. Later variants
  follow the same rule.
- Services may live in different deployments and share one Cassandra cluster or
  one Redis.
- A second instance of the same service on the same backend fails at start.
- A silent instance can be replaced. A new deployment can claim the service
  after the lease of the silent instance expires.

The key service and the secrets store are not store services under this rule.
The node id lease already keeps generated keys unique per node.

A store service with no shared backend claims nothing. A memory store lives in
one process.

### Why a store-side lease and not a registry entry

One store can be reached by deployments that do not share a registry. A Consul
check cannot see such a deployment. The store sees every deployment that
writes to it. The node id lease uses the store for the same reason.

### What the rule gives today

One core process opens all its store services together. So a second complete
core on one backend fails at its first service lease. That closes gap 1 and
gap 2 for every launch path that exists today.

The rule does not block a later split of services across deployments. That
split is the owner's longer-term direction, and it is out of scope here.

## Approaches considered

1. **A lease per store, for Lucene processes only.** It ignored a second core
   with another index type. It also refused a safe split by domain.
2. **A lease per store, for every core.** It forbade a split by service, which
   the owner's direction needs.
3. **Detect at start with the node id table, no new lease.** It was one-sided.
   A process that starts after the owner was not stopped.
4. **A lease per store service.** Chosen. It states the rule as the owner gave
   it, and it allows the later split.

## Lease names and scope

- A lease name is `<kind>:<domain>`. The kind is `persistence` or `index`.
  Examples: `persistence:user`, `index:topic`, `index:keyvalue`.
- The scope is per key type per backend, as for the node id lease. Redis
  carries the key type in the lease key. Cassandra separates key types by
  keyspace.
- **A lease lives in the persistence backend.** The persistence store gives the
  data its identity. An index lease lives there too. A Lucene user index over
  Cassandra persistence claims `index:user` in Cassandra, although its files are
  local.
- A lease never lives in the key backend. Two deployments that share only a key
  Redis do not conflict.
- A process claims when `app.service.core.persistence` names `redis` or
  `cassandra`.

## Mechanism

### The lease store

`ServiceLeaseStore` in `chat-core` takes a lease name. It has `claim`, `renew`
and `release`. Each returns a `Mono`, and the result is `Granted`,
`Denied(holder)` or `Lost`, as for `NodeIdClaimStore`.

- **Redis.** The key is `chat:lease:<keyType>:<name>`. A claim uses `SET NX PX`.
  A renew and a release use Lua compare-and-set scripts, as the node id store
  does. The Redis server is the clock.
- **Cassandra.** A new table in both keyspace scripts:

  ```cql
  CREATE TABLE chat_long.service_lease(
      name     text,
      owner_id text,
      PRIMARY KEY(name)
  );
  ```

  A claim is `INSERT ... IF NOT EXISTS USING TTL`. A renew and a release are
  conditional on `owner_id`. The coordinator applies the TTL.
- The store shape check requires `service_lease`. Under the no-migration rule of
  2026-09-25, a store from before this change fails its start, and the error
  names the missing table. The operator recreates the store from
  `keyspace-*.cql`.
- The truncate scripts do not list `service_lease`. A live lease must expire. A
  test cleanup must not delete it. This follows `node_claim`.

### What a process claims

The process claims one lease for each store service that it creates. The list
comes from the service beans that are present, not from a fixed list. A later
deployment that runs some of the services claims only those.

### The guard

`ServiceLeaseGuard` in `chat-core`:

- It claims every lease during the context refresh, before `RootKeyStartup`. So
  it holds the leases before any start scan reads the store.
- It claims in a fixed sorted order of lease names.
- A refused claim releases the leases that the guard already holds, and it fails
  the start. The message names the service, the scope and the holder.
- It renews on the node id schedule, with the node id properties:
  `app.nodeid.claim.ttl`, `renew-interval`, `safety-margin` and
  `operation-timeout`.
- A lost lease, or a missed deadline, closes the context, as the node id guard
  does.
- It reuses `ClaimScheduler`. Where the renew loop would copy
  `NodeIdClaimGuard`, the plan moves the loop to one place.
- The holder text names the node id and the runtime owner id, so an operator can
  find the deployment that holds a service.

The node id lease keeps its Redis key format and its `node_claim` table.

### Release on close

`CHAT-ocpojbyy` measured that a Redis close does not release the node id lease.
`LettuceConnectionFactory` is a `SmartLifecycle`, so it stops before singletons
are destroyed, and the release then fails.

Both guards become `SmartLifecycle` beans whose phase stops them before the
connection factory stops. The release then runs on a live connection. This
change closes `CHAT-ocpojbyy`.

## Failure cases

- An unreachable backend during a claim fails the start after the operation
  timeout.
- A renew error is logged and retried at the next interval. The deadline closes
  the context when the renewals do not succeed in time.
- A refused claim never leaves a partial set of leases.
- A process that comes back after a long pause and finds its lease lost closes
  its context. A TTL lease does not protect against a pause longer than the
  safety margin. The node id lease has the same limit.

## Tests

### Tests that prove the rule

- **Lease store contract, per backend, against real containers.** Claim, a
  denial that names the holder, renew, loss after the TTL, release by the owner
  alone, and a new claim after expiry.
- **Guard unit tests.** The claim order, the release after a refused claim, the
  close on loss, and the message text.
- **Two cores on one Redis.** `RedisSharedStoreIndexTests` changes from a
  measurement of the gap to a refusal test. The second core fails at start and
  names the service and the holder. A control shows that the first core still
  serves.
- **Swap.** Core A stops renewing and its lease expires. Core B then starts and
  finds the user of core A through its rebuilt index.
- **Release on close.** A Redis restart with the same node id starts at once,
  with no wait for the TTL.
- **Cassandra.** The same refusal on one keyspace, and a shape check refusal for
  a store without `service_lease`.
- **Mutation proofs.** With the guard disabled, the refusal test fails. With the
  release order repair removed, the restart test fails.

### The suite and the rule

Store-backed deploy test classes keep several contexts open on one store today.
That breaks the rule. The owner chose one context at a time.

- Store-backed deploy test classes close their context at class end, with
  `@DirtiesContext` or `use`.
- A class that runs two cores together on purpose becomes a refusal test.
- **The size of this work is not known.** The plan runs `build-health.sh --ci`
  once with the guard on, and the failures name the classes.
- The `--ci` gate must end with 0 failures. `docs/BUILD-HEALTH.md` records the
  count changes and their cause.

## Documents

- New `docs/SERVICE-LEASE.md`: the rule, the lease names and scope, the swap
  procedure, the properties, and the store recreation step.
- `docs/NODEID-CLAIM.md`: correct the Redis release statement, and record the
  node ids of the new tests.
- `docs/BUILD-HEALTH.md`: the new counts.
- The KDoc of `IndexStartSequence`: the start scan limit is now enforced by the
  lease.
- Drift: check `drift refs` for each changed file. Bind `docs/SERVICE-LEASE.md`
  to the lease sources.

## Out of scope

- A split of services across deployments. The rule allows it. No launch path
  does it today.
- A Redis Stack vector store (`vector=redis`). The key and persistence
  selectors do not name it. It stays an operator rule.
- Check-then-act races inside one instance, such as two concurrent adds of one
  handle. Those are older and separate.
- A change feed between indexes. The rule makes it unnecessary.
