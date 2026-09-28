# Plan: Cassandra authorization index repair (CHAT-rmxxtwtu)

Issue: `CHAT-rmxxtwtu`. Worktree: `.worktrees/auth-index`, branch
`chat-rmxxtwtu-auth-index` off master `61b6aff2`.

This plan repairs the Cassandra authorization index so Cassandra can prove
runtime grant continuity for the shipped `MESSAGE_TOPIC` root. It also fixes
the Cassandra CI flake `CHAT-sgyaaivp`, because the flake blocks reliable
verification of this work. The flake fix lands first on the same branch.

## The defects

**Flake `CHAT-sgyaaivp`: the driver timeout property is a no-op.** Both
session config classes build their own driver session and never read
`spring.cassandra.request.timeout`. The base `getDriverConfigLoaderBuilderConfigurer()`
returns null, so the session runs at the driver default of 2 seconds
(`DefaultDriverOption.REQUEST_TIMEOUT` default `PT2S`). The two regression
tests only assert the property is bound, which proves nothing. The 09-26 reds
ran at that default and timed out in the truncate setup.

**Defect one: one row per target and per principal.**
`keyspace-long.cql` and `keyspace-uuid.cql` declare
`auth_metadata_target PRIMARY KEY (target)` and
`auth_metadata_principal PRIMARY KEY (principal)`. The entities carry one
`@PrimaryKey` and no clustering column. A second grant on one target
overwrites the first.

**Defect two: `rem` deletes the wrong row.** `AuthMetadataIndex.rem` calls
`deleteById(key.id)`, but the primary key is the target and the principal,
not the grant id. It deletes the row whose target equals a grant key id.

**Why the removal needs its own table.** `CoreAuthorizationService` removes
the domain row before it calls the index removal, line 92 to 93. The removal
therefore cannot read the domain table for the grant's target and principal.
A by-id table supplies those coordinates. This mirrors `kv_pair_index_by_id`
from `CHAT-sgdtqhof`. The alternative of changing the `rem` signature was
weighed and declined. It would touch the interface, the lucene and memory
implementations, the in-memory fake and the controller.

**The grant key id is unique within a partition.** Each grant has one key,
and one row per grant per table. No two grants on one target share an id.
This holds for both the uuid and the long key type.

## Scope decisions, recorded

- Removal uses a by-id index table.
- The flake fix covers the shared test config and the deploy tests.
- The shape check requires the new by-id table. Its presence implies the
  operator recreated the store, so the clustering columns are present too.

## Schema

Add `id` as a clustering column to both index tables, in both keyspaces.

```
auth_metadata_target    PRIMARY KEY ((target), id)
auth_metadata_principal PRIMARY KEY ((principal), id)
```

Add a by-id table that maps a grant key to its two partitions.

```
auth_metadata_by_id
    id UUID, target UUID, principal UUID,
    target_root UUID, principal_root UUID,
    PRIMARY KEY (id)
```

**The primary key is the id alone.** This plan first wrote
`PRIMARY KEY ((id), target, principal)`. One grant has one id, so the two
extra columns add no uniqueness. They only widen the row. The built form uses
the id alone.

The by-id row carries the roots, so a recreated store is self describing.
The truncate scripts already name `kv_pair_index_by_id`. Add
`auth_metadata_by_id` beside each auth table truncate.

## Files

The change touches nineteen files. That exceeds the five file plan
threshold, so this plan exists.

### Flake fix, six files

1. `chat-persistence-cassandra/.../test/TestReactiveCassandraConfiguration.kt`
   Override `getDriverConfigLoaderBuilderConfigurer()`. Set
   `DefaultDriverOption.REQUEST_TIMEOUT` to `props.request.timeout` on the
   `ProgrammaticDriverConfigLoaderBuilder`.
2. `chat-deploy-cassandra/.../deploy/cassandra/dse/ContactPointConfiguration.kt`
   The same override. The deploy restart test starts through this config.
3. `chat-deploy-cassandra/src/test/resources/application.yml`
   Set `spring.cassandra.request.timeout` to 10 seconds. The container base
   stays untouched. The property is a test configuration value, so it belongs
   in the test yml.
4. `chat-persistence-cassandra/.../TypedKeyValueStoreTests.kt`
   Keep the property assertion. Add the effective driver timeout. Read it from
   the session config: `session.context.config.defaultProfile.getDuration(...)`.
5. `chat-index-cassandra/.../UserIndexRepositoryTests.kt`
   The same replacement.

The override applies the timeout only when the property is set. When it is
unset, the driver keeps its own default. That is the intended behaviour, so
the override reads the property and leaves it null otherwise.

### Auth index, nine main files

1. `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql`
2. `shared-resources-cassandra/src/main/resources/keyspace-long.cql`
   The two index table primary keys and the by-id table.
3. `shared-resources-cassandra/src/main/resources/truncate-uuid.cql`
4. `shared-resources-cassandra/src/main/resources/truncate-long.cql`
   The by-id table truncate.
5. `chat-index-cassandra/.../domain/AuthMetadata.kt`
   Convert both entities to composite keys with `id` clustered. Add the
   by-id entity and its composite key class.
6. `chat-index-cassandra/.../repository/AuthMetadataIndexRepository.kt`
   Add the by-id repository with a `findByKeyId` query.
7. `chat-index-cassandra/.../impl/AuthMetadataIndex.kt`
   `add` writes the by-id row. `rem` reads it, then deletes the target and
   principal rows by their full composite keys, then the by-id row.
8. `chat-index-cassandra/.../config/index/cassandra/CassandraIndexServices.kt`
   Pass the by-id repository to the index.
9. `chat-index-cassandra/.../config/index/cassandra/IndexServiceConfiguration.kt`
   Inject the by-id repository. Add `auth_metadata_by_id` to the shape check
   table map with its root columns.

### Auth index, test and doc files

The plan named four. The built change touches eight, because the new
repository parameter reaches two Spring wiring tests and one shape test.

1. `chat-index-cassandra/src/test/.../AuthMetadataIndexRepositoryTests.kt`
   Extend. It exists and saves one row per target. Add two grants on one
   target and read both. Remove one and prove the other stands. Cover the
   principal side too.
2. `chat-index-cassandra/src/test/.../AuthMetadataIndexTests.kt`
   Extend. It mocks the two repositories. The index takes a third by-id
   repository, so add its mock and a `findByKeyId` stub. This is the unit
   proof of the removal algorithm.
3. `chat-deploy-cassandra/.../CassandraGrantRestartTests.kt`
   Switch the target from `KEY_VALUE_PAIR` to `MESSAGE_TOPIC`. Add two grants
   on that root beside the shipped rows. Prove both survive a restart. Remove
   one and prove the other stands. This is the `MESSAGE_TOPIC` continuity
   proof the parent issue needs.
4. `docs/ANONYMOUS-AUTHORIZATION.md`
   Replace the one line per target warning. State that Cassandra now holds
   every grant row and the matrix applies to it. Name the new test as the
   proof. **The built document is narrower.** It states the structural
   blocker is gone and refuses the matrix claim, because no run measured the
   matrix rows against Cassandra. See the divergence note below.
5. `chat-index-cassandra/src/test/.../CassandraIndexRootTests.kt`
   Add the by-id repository mock and the new key class.
6. `chat-index-cassandra/src/test/.../CassandraIndexBeansConditionTests.kt`
   Add the by-id repository bean to the condition test.
7. `chat-persistence-cassandra/src/test/.../CassandraStoreShapeCheckTests.kt`
   Add the `id` columns and the by-id table to the required set. Add
   `auth_metadata_by_id` to the missing element cases. This is the proof that
   an older store fails the start check.

## Removal algorithm

`rem(key)` receives the grant key alone.

1. Read the by-id rows for the grant id.
2. Delete the target row by `(target, id)`.
3. Delete the principal row by `(principal, id)`.
4. Delete the by-id row.

The `add` mirrors this. It writes the target row, the principal row and the
by-id row. A repeated add of the same grant overwrites all three, because the
primary keys are unchanged.

## Verification gates

Each gate logs to a file. Read the exit code and the summary lines alone.

1. Focused index tests, full reactor from the worktree root.
   `mvn -o -B -pl chat-index-cassandra -am test`.
2. Focused persistence and deploy integration tests, with the container
   profile. Run the named classes only.
3. The flake gate. Run the previously red persistence and index integration
   classes. Record the result and whether it matches the document.
4. `build-health.sh --ci`. The schema change affects the image. Record the
   count and any drift.
5. `drift check` for the bound document.
6. `git diff --check`.

Record exact test counts and failures for each gate.

## Open items to confirm during implementation

- The driver config read path for the regression assertion. The session
  exposes its config through the driver context. Confirm the exact getter
  names on driver 4.19.3 with `javap` before writing the assertion.
- Whether `CassandraContainerBase` needs a distinct `app.nodeid` per class,
  per `docs/NODEID-CLAIM.md`. The restart test owns node id 23.
- Whether the deploy restart test reaches the new by-id table without a
  schema change in the test container, given the init script loads the
  updated `keyspace-uuid.cql`.

## Measured results

Every gate ran on 2026-09-27 on branch `chat-rmxxtwtu-auth-index`.

| Gate | Result |
|---|---|
| `AuthMetadataIndexTests` | 5 tests, 0 failures, 0 errors |
| `AuthMetadataIndexRepositoryTests` (`-Pintegration`) | 9 tests, 0 failures, 0 errors |
| `CassandraStoreShapeCheckTests` and `TypedKeyValueStoreTests` | 14 tests, 0 failures, 0 errors |
| `CassandraGrantRestartTests` (`-Pintegration`) | 1 test, 0 failures, 0 errors |
| `UserIndexRepositoryTests` | 4 tests, 0 failures, 0 errors |
| `CassandraAuthorizationMatrixTests` (`-Pintegration`) | 1 test, 0 failures, 0 errors |
| `build-health.sh --ci` | 28 modules ran tests, 1516 tests, 0 failures, 0 errors, 59 skipped. Reality matches `docs/BUILD-HEALTH.md`. Exit 0 |
| `drift check` | ok |
| `git diff --check` | exit 0 |

The shape check takes 253.7 seconds, because it builds and drops one keyspace
per case.

**The first `--ci` run exited 1 on the image build**, with
`'username' must not be null` from `spring-boot:build-image` on
`chat-deploy-memory-integration-tests`. That is the stale Docker Hub login
entry in `~/.docker/config.json`, and it names no Docker cause. The re-run set
`DOCKER_CONFIG` to a scratch directory holding `{}`. **The owner's Docker
config was not changed.** The workaround is the recorded one.

### The mutation proof, and what it found

A mutation that deleted the by-id row alone left `CassandraGrantRestartTests`
green. A grant read joins the index to the domain store, so the removal of the
domain row alone denies the permission. The leaked index row is invisible on
that path. The same mutation fails `AuthMetadataIndexRepositoryTests` with
three failures, because that test reads the index tables alone.

**So the restart test alone is not the proof.** The restart test carries a KDoc
that says so. Each test names its own evidence class.

### Two divergences from this plan

1. **The by-id primary key.** The plan wrote `((id), target, principal)`. The
   built form uses `(id)` alone. One grant has one id, so the extra columns
   add no uniqueness.
2. **The matrix claim.** The plan told the document to state that the
   authorization matrix applies to Cassandra. The first built document refused
   that claim, because no run had measured it. **This plan added the run.**
   `CassandraAuthorizationMatrixTests` measures all six rows against a
   cassandra store and index, and the document now carries the result. The
   self authority, many target and expiry cases stay map-store readings.
3. **`send` does not work on a cassandra deployment.** The first matrix test
   called `messageService().send` and the driver refused the write with
   `Codec not found for requested operation: [TIMESTAMP <-> java.lang.Long]`.
   `msg_id` is TIMESTAMP in three message tables, and the entities map a `T` id
   there. The test mints the message key instead. `CHAT-xcmpudyb` holds the
   defect. **No test sends a message on a cassandra deployment**, so no build
   reports it.

## Review boundary

Stop after the bounded implementation and the focused verification. Do not
push or open a pull request without review approval. Do not merge without
explicit approval.
