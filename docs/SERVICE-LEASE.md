# Store service lease

Issue: `CHAT-lswjobhz`. Spec:
`docs/superpowers/specs/2026-10-09-store-service-lease-design.md`.

Each Lucene index lives in one process. Two core processes on one shared
store each keep their own index, and neither index sees the writes of the
other process. The store service lease keeps a second core of the same store
services off a shared store.

## The rule

**On a shared backend, each store service has at most one current lease
holder.**

The owner's goal is one live instance of each store service. **A TTL lease
cannot prove that goal.** It cannot prove that an old process stopped. See
`The guarantee`.

A store service is one domain variant of persistence or of index. It is what
a `PersistenceServiceBeans` or `IndexServiceBeans` holder returns. An RSocket
controller that implements `PersistenceStore` or `IndexService` is not a
store service.

The key service and the secrets store are not store services. The node id
lease keeps generated keys unique per node. See `docs/NODEID-CLAIM.md`.

## Lease names

A lease name is `<kind>:<domain>`. The kind is `persistence` or `index`.

| Domain interface | Lease name |
|---|---|
| `UserPersistence` | `persistence:user` |
| `TopicPersistence` | `persistence:topic` |
| `MessagePersistence` | `persistence:message` |
| `MembershipPersistence` | `persistence:membership` |
| `AuthMetaPersistence` | `persistence:authmeta` |
| `KeyValueStore` | `persistence:keyvalue` |
| `UserIndexService` | `index:user` |
| `TopicIndexService` | `index:topic` |
| `MessageIndexService` | `index:message` |
| `MembershipIndexService` | `index:membership` |
| `AuthMetaIndex` | `index:authmeta` |
| `KeyValueIndexService` | `index:keyvalue` |

`ServiceLeaseNames` reads each zero-argument method of each holder, and it
maps each service through its domain interface. **A store service with no
mapped domain interface fails the start.** The message names the bean and its
class. A full core claims all twelve names.

## Scope and backend

- **The lease lives in the persistence backend.** An index lease lives there
  too. A Lucene user index over Cassandra persistence claims `index:user` in
  Cassandra, although its files are local.
- A process claims when `app.service.core.persistence` names `redis` or
  `cassandra`. A process with memory persistence claims no service lease, and
  it has no write gate.
- A lease never lives in the key backend. Two deployments that share only a
  key Redis do not conflict.
- The scope is per key type per backend. Redis carries the key type in the
  key `chat:lease:<keyType>:<name>`. Cassandra separates key types by
  keyspace, in the table `service_lease`.

## The guarantee

1. **At most one process holds a service lease at one time**, as the backend
   measures time.
2. **If the holder process does not pause for longer than the safety margin,
   it starts to close its own context before its lease can expire.** The
   close starts `ttl - safety-margin` after the send of the last granted
   claim or renewal of a lease. The defaults give 25 s.
3. **A holder that pauses longer than the safety margin can still write.** A
   garbage collection pause, a suspended host or a starved thread can delay
   the deadline check. The lease can expire, and a new holder can claim the
   service. The old holder can then complete writes that were in flight.
   Those writes can miss the index of the new holder.

The lease does not fence writes. Fencing needs a token check on every
persistence write path, and it is out of scope.

## The swap

**A safe swap needs confirmed termination of the old process.**

1. Stop the old deployment.
2. Confirm that its process exited. For example, the container stopped, or
   the process id is gone from the host.
3. Start the replacement.

A clean stop releases the leases, so the replacement starts at once.

**A wait of one `ttl` after the old process was last seen is not proof.** A
paused process can resume after its lease expired. A swap without confirmed
termination keeps the risk of case 3.

A start that meets a held lease fails with a message that names the service,
the scope and the holder. The holder text is `node <app.nodeid> <runtime owner
id>`, so an operator can find the deployment.

## Properties

The lease uses the node id lease timings.

| Property | Default | Use |
|---|---|---|
| `app.nodeid.claim.ttl` | `30s` | The lease TTL |
| `app.nodeid.claim.renew-interval` | `10s` | The time between renewal rounds |
| `app.nodeid.claim.safety-margin` | `5s` | The close deadline is `ttl - safety-margin` |
| `app.nodeid.claim.operation-timeout` | `5s` | The timeout of one claim, renewal or release |
| `app.service.lease.drain-timeout` | `10s` | The longest wait for active store writes at a close |

## The renew loop

- Renewal starts before the first claim. So a slow claim batch cannot let an
  early lease expire.
- A claim batch that passes the deadline of a held lease fails the start. The
  guard releases every lease that it took.
- One renewal round sends every renewal together.
- The deadline check runs on its own thread, and it never blocks. A renewal
  that blocks cannot delay it.

## Start and stop order

Spring starts phases in ascending order and stops them in descending order.

| Bean | Phase |
|---|---|
| `LettuceConnectionFactory` | `0` |
| `NodeIdClaimGuard` | `Int.MAX_VALUE - 6144` |
| `ServiceLeaseGuard` | `Int.MAX_VALUE - 5120` |
| `CommandRuntimeLifecycle` | `Int.MAX_VALUE - 4608` |
| `RootKeyStartup` | `Int.MAX_VALUE - 4096` |
| Reactive web server | `Int.MAX_VALUE - 2048` |
| RSocket server | `Int.MAX_VALUE` |

So the guards hold their leases before any start scan and before any request.
At a close, the servers and the command runtime stop first. The service guard
then drains the writes and releases. The connection factory stops last, so
each release runs on a live connection.

**A failed start releases every lease.** Spring 7 stops the started lifecycle
beans when a refresh fails. Each guard also releases in `destroy`, as a second
path.

## The write drain

A server stops before the guards. A request that it accepted can still be
running, and a canceled reactive chain does not prove that its store write
stopped.

- A `StoreWriteGate` counts each store write, from its start to its end in
  the store. A caller that cancels does not end the count.
- The gate wraps each `add`, `rem` and `addEnriched` call of each store
  service. Reads pass through.
- A Lucene index is not wrapped. It implements `ProcessLocalIndex`, because
  its writes go to local files.
- At a close, the service guard closes the gate and waits for the active
  writes. A closed gate refuses each new write with
  `StoreWriteRefusedException`. The renewals continue during the wait.
- A drain that ends in time releases the leases. A drain that passes
  `app.service.lease.drain-timeout` keeps the leases, and they expire after
  `ttl`. The log names the number of active writes.

**One limit stays.** The memory command bus can accept a command that has not
reached the store when the gate closes. Its write is then refused, and the
command is lost. Stage 1 retry safety already ends at a process restart.

## Store recreation

The Cassandra keyspace scripts hold the `service_lease` table. The store
shape check requires it. A keyspace from before this change fails its start,
and the error names the missing table. Recreate the keyspace from
`keyspace-long.cql` or `keyspace-uuid.cql` in `shared-resources-cassandra`.
No migration exists.

The truncate scripts do not list `service_lease`. A live lease must expire. A
test cleanup must not delete it.

## Split deployments

The lease names permit a future split of store services across deployments.
**They do not make a split safe.** A persistence writer in one deployment
changes the store, and the index owner in another deployment does not see the
change. **Split deployments stay unsupported until a persistence writer has a
path to the separate index owner.** No launch path splits services today.

## Tests

| Class | What it proves |
|---|---|
| `LeaseKeeperTimingTests` | Slow claims, a claim batch past a deadline, blocked renewals, one renewal round |
| `ServiceLeaseNamesTests` | The name map, the refusal of an unmapped service, the holder source |
| `ServiceLeaseGuardTests` | Claim order, refusal, holder text, release, loss |
| `StoreWriteGateTests` | The count, cancel, refusal, the post processor rules |
| `ServiceLeaseGuardDrainTests` | Drain before release, refusal after close, drain timeout |
| `RedisServiceLeaseStoreTests`, `CassandraServiceLeaseStoreTests` | The store contract |
| `RedisServiceLeaseBootTests` | Restart, swap, seeded lease, failed start, loss, drain |
| `RedisSharedStoreIndexTests` | A second Redis core is refused |
| `CassandraServiceLeaseBootTests` | A second Cassandra core is refused, restart, every service gated |
| `LeasePhaseOrderTests` | The phase order |
