# Capability Composition

Replaces the `chat-deploy-<backend>` modules with capabilities that are declared,
resolved and validated at startup, so one artifact can be deployed as a modulith
or as a microservice and can mix backends per capability.

Status: design, approved 2026-08-24. No implementation plan yet.

## The problem

Each `chat-deploy-<backend>` module names a data store, but a store is not a
deployment. The capability matrix is sparse, so no store covers everything:

| capability | memory | redis | cassandra | kafka | lucene | xstream |
|------------|--------|-------|-----------|-------|--------|---------|
| `key`         | yes (default) | yes | yes | — | — | — |
| `persistence` | yes (default) | yes | yes | — | — | — |
| `index`       | — | — | yes | — | yes (default) | — |
| `pubsub`      | yes (default) | — | — | yes | — | `redis-pubsub`, `redis-xstream` |
| `secrets`     | yes (default) | — | yes | — | — | — |

`--redis` therefore means redis for key, persistence and pubsub, Lucene for index
and memory for secrets. The module name says none of that, and the module did not
contain it either: fixing that one backend took six separate gaps, each found by
booting and reading the next stack trace (PR #47).

Three consequences, all of which this design targets:

1. There is no way to ask what coverage exists. The matrix above was assembled by
   grepping `@ConditionalOnProperty`; it is not data anywhere.
2. There is no way to mix. `key` from redis with `persistence` from cassandra is
   expressible in Spring properties and impossible in the module layout.
3. Silent defaults hide mistakes. `matchIfMissing = true` on the memory providers
   means an unset or misspelled selector resolves to memory rather than failing.

The Spring layer is already fine-grained: every provider is gated on one capability
and one value. The coarseness lives in the deploy modules and in `chat-build`'s
mutually exclusive backend group, not in the wiring.

## Decisions

| # | Decision |
|---|----------|
| 1 | One classpath carrying every provider; selection happens at launch, not at build |
| 2 | Strict validation: an unset, unknown or uncovered capability fails at startup |
| 3 | Providers declare coverage by annotation, read from bytecode |
| 4 | `rsocket` is a capability value, not a separate selector family |
| 5 | At most one provider per capability, enforced before the context starts |
| 6 | Compositions are partial and mix-and-match |
| 7 | Clients discover by structured query; derived names are the override path |
| 8 | A partition is a data domain; one partition per instance |
| 9 | The store carries a keyType and partition stamp, verified at startup |

## Model

### Providers declare what they supply

```kotlin
@ProvidesCapability(capability = "persistence", value = "redis")
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "redis")
class RedisPersistenceServices<T, V> : PersistenceServiceBeans<T, V> { ... }
```

### Consumers declare what they require

```kotlin
@RequiresCapability("persistence")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class PersistenceControllerConfiguration { ... }
```

`capability` and `value` are strings rather than an enum. `chat-crypto` and
`chat-presence` already select on `app.service.crypto.backend` and
`app.service.presence.backend`, so the set stays open to extension without editing
core.

The annotation restates what `@ConditionalOnProperty` says and the two can
disagree. One test over the scanned registry asserts that every
`@ProvidesCapability` matches the condition on the same class. That test is the
whole mitigation and it is not optional.

Scanning reads annotations from bytecode, so the registry sees every provider
whether or not its condition matched — which is the point. Coverage has to include
what did not activate.

### Compositions are partial

A composition names only the capabilities a backend genuinely serves:

```yaml
# chat-deploy/src/main/resources/compositions.yml
compositions:
  memory:      { index: lucene }
  redis:       { key: redis, pubsub: redis-pubsub }
  cassandra:   { persistence: cassandra, secrets: cassandra }
  core-client: { key: rsocket, persistence: rsocket, index: rsocket, pubsub: rsocket, secrets: rsocket }
```

Selecting `memory`, `redis` and `cassandra` together yields `key=redis`,
`persistence=cassandra`, `index=lucene`, `pubsub=redis-pubsub`,
`secrets=cassandra` — complete cover, no overlap, and a combination no existing
deploy module can express.

Two declarations doing different jobs:

- **Providers** declare what exists. Annotation on the class, bytecode-scanned,
  cannot drift.
- **Compositions** declare which combinations are blessed. This is policy, and
  policy has to be written down or it stays folklore.

### Resolution

An `EnvironmentPostProcessor` runs before the context and:

1. unions the selected compositions,
2. checks every required capability is covered exactly once, where *required*
   means declared by a `@RequiresCapability` on a consumer whose own condition the
   launch flags satisfy — a deployment that exposes no persistence controller does
   not require persistence,
3. checks each resolved value has a provider on this classpath,
4. checks the launch flags match the composition they claim,
5. computes `spring.autoconfigure.exclude` from the result.

Step 5 is what makes one classpath viable rather than merely possible. The
Cassandra driver, Lettuce, the Kafka client and Lucene are all present in every
deployment, and Spring Boot's infrastructure auto-configurations trigger on
classpath presence — `CassandraAutoConfiguration` builds a `CqlSession`,
`RedisAutoConfiguration` a connection factory. Without exclusion a memory
deployment opens connections to stores it never selected and fails on the ones
that are not running. Capabilities resolve first; everything not backing a
resolved capability is excluded before auto-configuration runs.

Resolution happens in an `EnvironmentPostProcessor` rather than a bean precisely
because it must precede auto-configuration.

## Topology

Modulith and microservice are not deployment modes. They are counts of how many
capabilities resolved to `rsocket`.

### Remote is a value

Today the same question is asked twice:

| | local | remote |
|---|---|---|
| persistence | `app.service.core.persistence=redis` (valued) | `app.client.rsocket.core.persistence` (bare flag) |

The bare flags are replaced by `app.service.core.<capability>=rsocket`. One
selector per capability, one matrix, and a mixed deployment needs one convention
rather than two. `app.client.protocol` and `app.client.discovery` stay as they
are — they describe how to reach a remote, not which capability it serves.

### Registration

```
name: core-service-rsocket-cassandra-west
tags: capability:persistence, capability:secrets, backend:cassandra, keyType:long, partition:west
```

Both derived from the one resolution, so they cannot disagree. The name is what a
human wires and reads; the tags are what a query filters on. `keyType` is already
advertised today and read by nothing; this gives it a job.

`instance-id` must become unique. It is currently `${spring.application.name}`, a
constant, so a second instance re-registers the same id and overwrites the first
rather than joining as a second instance. Two processes cannot be represented as
two entries until this changes.

### Discovery

```kotlin
interface CapabilityDiscovery {
    fun discover(capability: String, keyType: String, partition: String): ServiceTarget
}
```

A client's own configuration supplies the defaults, so `discover("persistence")`
is the ordinary call. `app.client.targets.<capability>=<name>` overrides discovery
with an explicit service name, derived by the same rule the server registers
under; server and client share that derivation and a test asserts both produce the
same string.

**Ambiguity rule.** Matches sharing a backend are replicas — load balance freely.
Matches with different backends violate the invariant that
`(capability, keyType, partition)` maps to exactly one backend, so the client fails
and names both rather than picking. This is the cross-process form of decision 5.

**Enforcement boundary.** That check sees one registry. A store reachable from two
deployments with separate registries is real in this system, and neither side sees
the other. Decision 9 exists because of this boundary.

### Partition

A partition is a data domain — a shard or a tenant, holding different data rather
than a copy. A wrong-partition answer is a correctness failure, not a slower one,
so discovery filters strictly rather than ranking.

One partition per instance. The partition is resolved at startup alongside the
capabilities, appears in the name and the tags, and cannot vary per request. A
process holding one partition cannot leak across partitions.

`nodeId` is **not** a partition. It is a unique host identifier, and its
uniqueness problems are tracked separately in CHAT-koufkrsl.

## Store identity stamp

The store is the only component two deployments with separate registries share, so
it is the only place an assertion survives them.

The store carries a small record of the `keyType` and `partition` it holds. A
deployment reads it at startup and refuses to start on mismatch, and writes it only
when the store is empty. This catches what a shared store actually produces: a west
deployment attaching to the east store, or a Long deployment to a UUID store.

The stamp needs no lease, heartbeat or expiry, because what it records does not
change while the store exists. Live ownership — which deployment currently holds
which nodeId — does need those, and is out of scope here; see CHAT-koufkrsl.

Root keys already live in the store and are read at startup, so this follows an
established path.

## Reporting

A `capabilities` actuator endpoint, shaped like the existing `rootkeys` endpoint,
answers three separate questions:

- **resolved** — capability to backend, provider class, local or which remote target
- **possible** — the scanned provider matrix, including providers that did not activate
- **blessed** — the compositions

The first is what this deployment did. The second is what it could do. The third is
what has been sanctioned. Conflating them is how the matrix at the top of this
document became folklore.

## Failure messages

Following `e853b226`, which established naming what is missing rather than
reporting that something is absent:

```
Capability 'secrets' is not covered.
  compositions selected: memory[index], redis[key, pubsub]
  providers available:   memory, cassandra
  cover it, or set secrets=rsocket with a target

Capability 'persistence' is covered twice.
  redis[persistence], cassandra[persistence]
```

A resolution failure must never surface as `NoSuchBeanDefinitionException`. That
cascade is what made the redis backend unreadable: the first symptom named
`SecretsStoreBeans`, which is five layers from the actual cause.

## Migration

Ordered so that failures are legible before anything is deleted.

1. Annotation, registry scan, resolver — reporting only, no behaviour change
2. Annotate every provider; add the agreement test
3. `compositions.yml` describing today's backends as partial sets
4. Resolver goes strict; `matchIfMissing` comes off the memory providers
5. Provider dependencies fold into `chat-deploy`; delete `chat-deploy-memory`,
   `chat-deploy-redis`, `chat-deploy-cassandra`, `chat-deploy-kafka`; `chat-build`
   features become composition selections
6. Client selectors unify to `=rsocket` plus targets; `chat-shell`,
   `chat-authorization-server` and the yml property blocks follow
7. Service naming, tags, unique instance-id, partition, store stamp

Step 1 before step 5 is the point of the ordering: the resolver must be able to
explain a broken composition before the modules that currently imply one are
removed.

This is two implementation plans, not one. Steps 1 to 4 build the mechanism and
delete nothing, and the tree is releasable at the end of them. Steps 5 to 7 change
the topology — modules, images, selector names and registration — and each is a
breaking change to something outside this repository's control.

### What this costs

- **The per-backend images collapse.** `chat-deploy-redis:0.0.1` and its siblings
  stop existing. `chat-deploy-memory-integration-test`, whose image `chat-shell`'s
  integration tests boot, has to change with them.
- **Every launch names all five capabilities.** Removing `matchIfMissing` means
  `chat-build`, every `@TestPropertySource` block and the deployment ymls must be
  explicit.
- **`app.client.rsocket.core.*` is a breaking rename** with no alias period.

`chat-deploy-e2ee` and `chat-deploy-memory-integration-test` are not backend
modules and are out of scope. E2EE's `crypto` and `presence` selectors fit the
capability model and should move to it, but not in this change.

## Testing

- Registry agreement: every `@ProvidesCapability` matches the condition beside it
- Resolver: missing capability, doubled capability, unknown value, unset selector,
  composition claimed but flags disagree
- Auto-configuration exclusion: a memory deployment opens no Cassandra, Redis or
  Kafka connection
- Name derivation: the string a server registers under equals the string a client
  derives
- Store stamp: mismatched keyType and mismatched partition both refuse to start
- **A boot test per blessed composition**, generalising `RedisDeployBootTests`,
  container-backed where the backend needs one

The last is the one that matters. A disabled boot test is how the redis backend
rotted unnoticed: the classpath could not satisfy the flags `chat-build` emitted,
and the test that would have said so was `@Disabled` with a comment explaining
why. Every composition gets a boot test and they stay enabled.

`./shell-scripts/build-health.sh` must stay green in both modes throughout, since
several steps touch modules that every deployment shares.

## Out of scope

- **nodeId uniqueness** — CHAT-koufkrsl. Confirmed as host identity only, unrelated
  to partition. A store reachable from multiple registries means detection cannot
  be sufficient there; assignment or a store-side ownership claim is required.
- **Live ownership claims** — leases and heartbeats, needed for nodeId, not for the
  static stamp in decision 9.
- **Domain serialization** — CHAT-gjggodpa. Same erasure family, independent change.
