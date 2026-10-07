# Lucene indexes in files

Issue `CHAT-ybtirmgj`. Parent issue `CHAT-aqpcacwv`.

The owner approved the design in three sections on 2026-10-06. Each section
passed a review. This document includes the corrections of every review.

## Problem

Every Lucene index is in memory. `LuceneIndex.kt` line 30 creates a
`ByteBuffersDirectory`. Since `CHAT-uxgdzpag`, each start rebuilds all six
indexes from the store through `PersistedIndexLoad`.

Four defects sit in the same class. Each one matters more when the index is in
files.

1. `findBy` and `MembershipLuceneIndex.size` call `DirectoryReader.open` and
   never close the reader. On files, each query would hold file handles and
   keep deleted segment files on disk.
2. `onClose()` has no caller. No bean declares it as a destroy method, so the
   writer never closes.
3. The constructor calls `deleteAll()` and `commit()`. On files, that would
   destroy the stored index at each start.
4. `add` calls `addDocument`. A second add of one key stores a second document.
   `KeyValueLuceneIndex` removes and then adds, and that pair is not atomic.

Only one persistent store uses a Lucene index: Redis. The memory deployment and
the Kafka deployment use the memory store, so their store is empty after a
restart.

## Goal

- A start reuses the index files when their content equals the store content.
- A start builds an index when the files are missing, damaged, or different.
- A crash that leaves the store and the committed index different causes a
  build at the next start. A crash that leaves them equal allows reuse.
- Two node ids on one host use two directories.
- An operator can request a rebuild or a drop of one index. The request takes
  effect at the next start.
- With no configured root, every index stays in memory, as today.

## Owner decisions

All decisions are from 2026-10-06.

1. **A start with intact files saves the index writes only.** The start still
   reads every stored entity. The stores do not change.
2. **No root means memory mode.** A Redis operator sets the root to get files.
3. **The compare is exact.** It compares bytes and uses no hash. A hash can
   collide, and the issue requires "never a silent stale index".
4. **The rebuild command and the drop command take effect at the next start.**
   No index rebuilds while the process serves.
5. **Drop deletes the index files, then builds at the next start.** It never
   leaves an index disabled.
6. **A repeated store key fails the start.** The compare scan detects it with a
   bitset over document ids. The build scan detects it with a count.

### Two gaps filed, not repaired here

- `CHAT-lswjobhz`. Each Lucene index lives in one process. Two node ids can
  share one Redis store. A write through node A never reaches the index of node
  B. Another process can also write the store during the start compare. **So
  the compare is exact only when no other process writes the store during the
  start.**
- `CHAT-oltrsgws`. Two concurrent updates of one key can reach the index in
  the opposite order to the store. That race exists today. This work adds no
  live rebuild, so it does not widen the race. A start with files detects the
  result as a mismatch.

## Configuration and layout

`app.index.lucene.root` is optional.

- **Absent:** every index uses `ByteBuffersDirectory`. The start logs
  `lucene index storage: memory`.
- **Blank:** the start fails. The message names the property.
- **Set:** each index uses its own `FSDirectory` with `NativeFSLockFactory`.
  The start logs `lucene index storage: files at <root>`.

```
<root>/<app.key.type>/<app.nodeid>/<index>
index = user | message | topic | membership | auth | keyvalue
```

Two processes with the same key type and the same node id meet at
`chat-owner.lock`. The second process fails its start.

The path holds no store identity. A process that starts against a different
store finds a mismatch and builds. The path only keeps two live processes apart,
and the lock does that.

### Files in one index directory

| File | Owner | Purpose |
|---|---|---|
| `chat-owner.lock` | this design | Held from `open` to `close`. No other process can use the directory. |
| `write.lock` | Lucene | Held by the `IndexWriter`. |
| `chat-rebuild.request` | operator command | The next start builds. |
| `chat-drop.request` | operator command | The next start deletes the index files, then builds. |
| all other files | Lucene | Segments and commits. |

**No file of this design starts with `_` or with `segments`.** Lucene's file
deleter claims names of those forms. A test pins that a commit leaves the
request files in place.

**Recovery never deletes `chat-owner.lock`, `write.lock`, or a request file.**

## The document

Each document holds these fields.

| Field | Type | Purpose |
|---|---|---|
| encoder fields | `TextField`, not stored | Search, as today |
| `key` | `TextField`, stored | The key text that `findBy` returns, as today |
| `_key` | `StringField`, not stored | The exact key. Replacement and lookup use it. |
| `_enc` | `StoredField`, binary | The canonical bytes of the entry. The compare reads it. |

`addEntry` builds `_enc` from the same `fields` list that it uses for the
document. `requireNoReservedField` rejects an encoder field named `_key` or
`_enc`.

### The canonical encoding, format 1

All integers are int32, big-endian.

1. The format version.
2. The byte length of the key text, then the UTF-8 bytes of `key.id.toString()`.
3. The number of fields.
4. For each field, in encoder order: the byte length of the name, the UTF-8
   name, the byte length of the value, and the UTF-8 value.

**The fields are not sorted.** The encoder is deterministic, and its order is
the order that Lucene indexed. A repeated name stays as separate pairs in place.
So the bytes are the exact list that `addEntry` wrote.

Stored fields are compressed with LZ4. The index grows by about the size of the
indexed text. Messages are most of it.

### The commit header

Each commit stores this user data.

| Key | Value |
|---|---|
| `chat.format` | The format version, `1` |
| `chat.lucene` | `Version.LATEST` |
| `chat.analyzer` | The analyzer class name |
| `chat.keyType` | `app.key.type` |
| `chat.nodeId` | `app.nodeid` |
| `chat.index` | The index name |

Encoder changes and registered field changes show in the `_enc` bytes. Analyzer
changes do not. **A change to the document layout or to the analyzer
configuration must increase the format version.** The constant carries this
rule in its KDoc.

## The start sequence for one index

`LuceneIndexLoad` in `chat-index-lucene` runs this sequence. It takes the place
of `PersistedIndexLoad` for the six Lucene beans. `PersistedIndexLoad` stays in
`chat-core` for any other index.

A memory directory never holds a commit. It always takes the "no commit" branch,
so both modes share one path.

```
obtain chat-owner.lock ─────────── held elsewhere ─────────────────► FAIL START
delete stale .tmp request files, then read the request files
open IndexWriter, CREATE_OR_APPEND
   ├ damage class ─────────────────────────────────────────────────► recover, then build
   └ any other error ──────────────────────────────────────────────► FAIL START
drop request present ──────────────────────────────────────────────► recover, then build
rebuild request present ───────────────────────────────────────────► build
open committed reader, DirectoryReader.open(directory)
   ├ IndexNotFoundException: no commit ────────────────────────────► build
   ├ damage class ─────────────────────────────────────────────────► recover, then build
   └ any other error ──────────────────────────────────────────────► FAIL START
header differs ────────────────────────────────────────────────────► build
checkIntegrity on every leaf ─── damage class ─────────────────────► recover, then build
compare scan:
   store or encoder error ─────────────────────────────────────────► FAIL START
   a key reaches a document already marked ────────────────────────► FAIL START
   a live document without _enc ───────────────────────────────────► recover, then build
   a missing, extra, or different entry ───────────────────────────► build
   all equal, and numDocs() equals the entities scanned ───────────► REUSE
```

`CREATE_OR_APPEND` opens an empty directory without an error. So "no commit"
shows at the reader step.

**The owner lock comes first.** No step deletes a file before the lock is
held. The request files are read after the lock and before any destructive
step.

**When a drop request is present, the reason is `REQUESTED_DROP`**, whatever
other branch also applies. A rebuild request that meets damage builds with the
reason `DAMAGE`, and the build still removes the rebuild request.

The start load runs inside `RootKeyStartup`, before readiness. No local write
can arrive during the compare.

### The compare scan

The scan reads the committed index through an `IndexSearcher`. That searcher
applies `getLiveDocs()`, so a deleted document cannot match. `numDocs()` counts
live documents only.

For each store entity, in the order of `store.all()`:

1. Encode the entity with the index encoder.
2. Look up `_key`. Require exactly one live document.
3. Require that the document holds `_enc`.
4. Require that the bytes are equal.
5. Mark the document in a `FixedBitSet` of size `reader.maxDoc()`.

After the scan, require that `numDocs()` equals the number of entities scanned.

**A lookup that reaches a marked document proves a repeated store key.** Each
key has exactly one live document, so only a second store entity with the same
key can reach a marked document. The start fails. The bitset costs one bit per
document, which is 125 KB for 1M documents.

A repeated key with no document cannot reach the bitset. Its first occurrence
is a mismatch, so a build follows, and the build scan detects the repeat.

The scan can stop at the first mismatch. Any mismatch leads to a build.

### Integrity

`LeafReader.checkIntegrity()` checks the codec components of each segment and
their checksums. The component readers do the checks. The cost is about the
size of the index on disk. This design makes no claim about how many times each
byte is read, or in what order.

### The damage class

These errors mean damage, and they lead to recovery:

- `CorruptIndexException`
- `IndexFormatTooOldException`
- `IndexFormatTooNewException`
- `EOFException`
- `NoSuchFileException` for a file that a commit names
- a live document without `_enc`

These errors fail the start:

- `LockObtainFailedException`
- `AccessDeniedException`, and every other `FileSystemException`
- every other `IOException`, for example a full disk
- every store error and every encoder error

**A store error never becomes an empty or a partial result.**

### Recovery

1. Keep `chat-owner.lock` for the whole sequence.
2. Close every open reader. Roll back the writer if one is open. Rollback
   releases `write.lock`.
3. Obtain `write.lock` directly with
   `directory.obtainLock(IndexWriter.WRITE_LOCK_NAME)`. **If that fails, fail
   the start and delete nothing.** A process that ignores the owner lock can
   still hold `write.lock`.
4. Delete every file except `write.lock`, `chat-owner.lock`, and the two
   request files.
5. Release `write.lock`.
6. Open a new `IndexWriter` in `CREATE` mode. If that fails, fail the start.

Recovery never relies on `IndexWriter` to read a damaged commit.

### The build

1. Call `deleteAll`.
2. Read the store a second time. Add each entity with `updateDocument` on
   `_key`. Do not commit per entity.
3. Open an NRT reader with `DirectoryReader.open(writer, true, false)`. That
   reader applies the deletes.
4. Require that its `numDocs()` equals the number of entities scanned. If it is
   lower, a key repeated. Roll back and fail the start.
5. Close the NRT reader.
6. Commit once, with the new header.
7. Delete the request files. After a drop build, delete both, because drop
   supersedes rebuild. After a rebuild build, delete the rebuild request.
8. Sync the directory with `syncDirectory` (see Request durability).
9. Create the `SearcherManager`.

`rollback()` cannot undo a completed commit, so every check runs before the
commit.

**A failure before the commit, step 1 to step 5:** call `rollback()` and fail
the start. These two guarantees hold only when `rollback()` succeeds:

- **On the header path and the mismatch path, the previous commit survives.**
  Those paths use `deleteAll` with no commit.
- **On the recovery path, no previous commit remains.** The directory holds only
  the lock files and the request files. The next start takes the "no commit"
  branch and builds.

If `rollback()` also fails, the start fails with both errors, and this design
makes no claim about the directory.

**A failure in the commit, step 6:** the committed state is uncertain. The
commit may or may not be durable. Attempt `rollback()` as best-effort cleanup,
and fail the start. This design makes no claim about which commit survives.

**A failure after the commit, step 7 to step 9:** request deletion, directory
sync, or `SearcherManager` creation fails after a completed commit. The start
fails. Nothing tries to undo the commit.

**After any of these failures, the next start runs the full sequence.** It
reads the request files that remain, reads the committed state, and lets the
normal checks decide. No failed start leaves a result that the next start
trusts without that validation.

A crash between step 6 and step 7 leaves a request file. The next start builds
again. That is safe.

### The start outcome

Each index records
`StartOutcome(kind, reason, entitiesCompared, documentsWritten)`.

- `kind` is `REUSED` or `BUILT`.
- `reason` is nullable. It is null for `REUSED`. For `BUILT` it is one of
  `NO_COMMIT`, `HEADER`, `MISMATCH`, `DAMAGE`, `REQUESTED_REBUILD`, and
  `REQUESTED_DROP`, and it is never null.

The start logs one line per index:

```
lucene index user: reused, 42 entries compared, 0 written
lucene index user: built, reason=MISMATCH, 42 entries written
```

## The index lifecycle

`LuceneIndex` has four states: `NEW`, `OPEN`, `FAILED`, and `CLOSED`.

- **`open(storage)` is explicit.** It runs the start sequence. The start load
  calls it in production. Tests call it directly. A test helper
  `openInMemory()` keeps the direct-construction tests short.
- **No operation opens an index implicitly.**
- An operation in `NEW` fails with
  `IllegalStateException("Lucene index <name> is not open")`.
- An operation in `FAILED` fails with `"Lucene index <name> failed: <cause>"`.
- An operation in `CLOSED` fails with `"Lucene index <name> is closed"`.
- **A query during the start sequence fails**, because no `SearcherManager`
  exists before reuse or the build commit.
- A failed `open` moves the index to `FAILED`. It releases what it obtained,
  and it releases `chat-owner.lock` last.
- A new instance can `open` the same directory after `close`. That is the
  restart path of the tests.

### Mutations

`add` and `rem` run under one `ReentrantLock` per index. Under that lock they do
the writer change, the commit, and `maybeRefreshBlocking()`.

- `add` calls `updateDocument(Term(_key), doc)`. That replaces by key
  atomically.
- `KeyValueLuceneIndex.add` still reads and checks the fields first. Then it
  calls one `updateDocument`. The remove and add pair is gone.
- **Each runtime mutation commits.** On files, each commit calls fsync. A lost
  uncommitted write only causes a build at the next start, so batched commits
  would be safe later. The plan measures the cost and reports it. A batching
  change is a separate issue.

### Mutation failures

- **An error before any writer change keeps the index `OPEN`.** Encoder errors
  and reserved-field errors are in this class.
- **A writer error, a commit error, or a refresh error moves the index to
  `FAILED`.** The cleanup is best effort:
  1. Call `writer.rollback()`. It discards pending changes, closes the writer,
     and releases `write.lock`.
  2. Close the `SearcherManager`.
  3. If one step throws, run the remaining steps. Report the first error, with
     the others attached as suppressed.
- **Nothing commits during that cleanup.**
- `chat-owner.lock` stays held until `close()`. No other process takes the
  directory while this process lives.
- A refresh error after a successful commit leaves durable data behind the old
  searcher. `FAILED` stops the process from serving stale reads. The next start
  compares the committed data.

### Readers

- `SearcherManager(directory, null)` reads committed data only. It never takes
  the writer as its source. A refresh therefore cannot expose an uncommitted
  change.
- It is created after a validated reuse or after the first build commit. It is
  never created before either.
- Queries only `acquire` and `release`, in `finally`. They never take the
  mutation lock.

### Close

- `close()` is idempotent, and it is safe in every state.
- It takes the mutation lock, so it waits for a running `add` or `rem`.
- It closes the `SearcherManager`, the writer, the directory, and the analyzer,
  in that order. It releases `chat-owner.lock` last.
- If one step throws, the remaining steps still run. `close()` throws the first
  error, with the others attached as suppressed.
- **Close never commits.** The writer config sets `setCommitOnClose(false)`.
  Each runtime mutation commits by itself, and a build commits once after its
  checks. So close has nothing to commit, and it cannot commit a partial build.
- `close()` is the destroy method of each index bean.

## Operator commands

Both commands only write a request file. The next start acts on it. A running
index never changes because of a command.

| Command | File | The next start |
|---|---|---|
| rebuild | `chat-rebuild.request` | Builds. The previous commit survives until the new commit. |
| drop | `chat-drop.request` | Recovers, then builds. The previous commit does not survive. |

- **Drop is for an index that the operator does not trust at the file level**,
  beyond what `checkIntegrity` finds.
- A drop request takes precedence when both files exist.
- **In memory mode, both commands are refused** with `accepted=false`. The
  reason states that every start already builds.
- No command cancels a request. The operator note states how to delete a
  request file by hand.

### Request durability

A command writes a request in this order:

1. Write the temporary file `chat-<command>.request.tmp`.
2. Sync the temporary file with `FileChannel.force(true)`.
3. Move it to its final name with `ATOMIC_MOVE`.
4. Sync the directory with `syncDirectory`.

**`syncDirectory` propagates every failure.** It opens the directory with
`FileChannel.open(dir, READ)` and calls `force(true)`. Any `IOException` from
the open or from `force` reaches the caller. The helper takes the channel
opener as a parameter, with `FileChannel.open(dir, READ)` as the default. A
test can then pass a channel whose `force(true)` throws, and still run the real
helper body. **Do not use
`IOUtils.fsync(directory, true)`.** Lucene 8.7 suppresses an `IOException`
from `force()` on a directory, so that call cannot support the guarantee below.
The start sequence uses the same helper in step 8 of the build.

**The command answers `accepted=true` only after all four steps succeed.**

- If the file system refuses an atomic move, the command answers
  `accepted=false` with the cause. It never substitutes a non-atomic move.
- If a step before the move fails, the command answers `accepted=false`. This
  attempt installs no new request. Any previously pending request remains.
- **If the directory sync fails after the move succeeded,** the command answers
  `accepted=false`. The answer states that the request file can remain pending
  and that the next start can act on it. It also gives the request file path.

The start sequence deletes a stale `.tmp` file from a crashed command. It does
so after it obtains the owner lock, and before it reads requests. A `.tmp` file never counts as a request.

## Code placement

- `chat-index-lucene` gains `LuceneIndexRegistry`. It holds the six indexes.
  For each index it reports the name, mode, path, state, live document count,
  start outcome, and pending request. It also writes request files. **This
  module gains no actuator dependency.**
- `chat-deploy` gains `LuceneIndexEndpoint`, beside `VectorIndexEndpoint`.

### The endpoint

```kotlin
@Endpoint(id = "luceneindex", defaultAccess = Access.NONE)
@ConditionalOnBean(LuceneIndexRegistry::class)
```

Boot 4.0.8 defaults the annotation to `UNRESTRICTED`. This endpoint sets
`NONE` explicitly.

| Operation | HTTP | Effect |
|---|---|---|
| `@ReadOperation` | `GET /actuator/luceneindex` | The report for all six indexes |
| `@WriteOperation`, `@Selector name` | `POST /actuator/luceneindex/{name}` | Request a rebuild |
| `@DeleteOperation`, `@Selector name` | `DELETE /actuator/luceneindex/{name}` | Request a drop |

An unknown name answers `accepted=false`, and the answer names the six valid
names.

An operator enables the endpoint with two properties:

```
management.endpoint.luceneindex.access=unrestricted
management.endpoints.web.exposure.include=luceneindex
```

No deployment sets either. `ActuatorWebSecurityConfiguration` requires the
`ACTUATOR` role on every actuator route except `health`.

The shipped `management-defaults.yml` uses the deprecated key
`management.endpoints.enabled-by-default`. Its replacement since Boot 3.4.0 is
`management.endpoints.access.default`. That migration is a separate change.

## Tests

### Index unit tests, `chat-index-lucene`

- **Same key.** Two adds of one key give one hit. A query on a field value of
  the old entry gives no hit.
- **Two hits.** `MessageIndexTests` uses two distinct keys for its two-hit
  test. Its value supply reuses key `1234L` at `MessageIndexTests.kt:22`.
- **Reserved names.** An encoder field named `_enc` fails, and so does one
  named `_key`.
- **A query during a paused mutation.** The test pauses a runtime mutation
  after `updateDocument` and before `commit`. A query on another thread returns
  the previous committed data, and it does not wait. After the commit and the
  refresh, a new query returns the changed data.
- **Wrong state.** An operation before `open` fails with the stated message.
  An operation after `close` fails with the stated message. A second `close`
  does not throw.
- **Mutation failure.** A writer that fails at commit moves the index to
  `FAILED`. Later operations fail with the stated message. Nothing commits.
- **Encoder failure.** An encoder error before any writer change keeps the
  index `OPEN`.
- **Canonical bytes.** A golden test pins the format 1 bytes of one entry with
  a repeated field name.
- **Request files survive a commit.** The test writes both request files, adds
  an entry, and commits. Both files remain.

### Start sequence tests, `chat-index-lucene`

Each test uses a temporary root and an in-memory store stub.

| Criterion | Test |
|---|---|
| Intact files are not rebuilt | Open, add, close. Reopen against the same store. `REUSED`, `documentsWritten == 0`, and the data is found. |
| Missing files | Delete the index directory, then start. `BUILT/NO_COMMIT`, and the data is found. |
| Damaged files | Overwrite bytes inside a segment file, then start. `BUILT/DAMAGE`, and the data is found. |
| Different content | Add one entity to the store with no index add, which simulates a crash. Then start. `BUILT/MISMATCH`, and the entity is found. |
| Header change | Commit with a different format version, then start. `BUILT/HEADER`. |
| Repeated key, compare scan | The store emits key A twice, and the index holds A and B. The start fails, and no file is deleted. |
| Repeated key, build scan | The store emits key A twice, and the index has no commit. The start fails before any commit. |
| Store error | The store fails during the compare. The start fails, and the previous commit is intact. |
| Build failure, mismatch path | The store fails during the build. The start fails. The previous commit is intact. The next start builds. |
| Build failure, recovery path | Damage, then the store fails during the build. The start fails. Only the lock files and request files remain. The next start builds. |
| Rebuild request | `BUILT/REQUESTED_REBUILD`. The request file is gone after the commit. |
| Drop request | `BUILT/REQUESTED_DROP`. Both request files are gone after the commit. |
| Drop request, failed build | The drop request survives recovery and the failed build. The next start acts on it. |
| Two node ids | Node ids 1 and 2 on one root give two directories, and both open. |
| One node id twice | The second open fails on the owner lock. Nothing is deleted. |
| `write.lock` held elsewhere | A damaged index whose `write.lock` another holder keeps. Recovery fails the start and deletes nothing. |
| Blank root | The start fails, and the message names the property. |
| Stale temporary request | A `.tmp` request file is deleted and does not count. |
| Commit failure | The build commit fails through an injected writer. The start fails. The next start runs the full sequence. |
| Failure after commit | Request deletion fails after a completed commit. The start fails. The next start runs the full sequence and builds, because the request file remains. |
| Directory sync failure in a command | `syncDirectory` fails through an injected sync. The command answers `accepted=false`, and the answer states that the request can remain pending. |
| Directory sync, open failure | `syncDirectory` on a path that cannot be opened throws. It does not return normally. |
| Directory sync, force failure | The opener returns a channel that opens normally, and its `force(true)` throws. The real `syncDirectory` throws that error. |

### Endpoint tests, `chat-deploy`

1. **Shipped defaults.** `GET /actuator/luceneindex` answers 404.
2. **The annotation alone.** This test isolates `defaultAccess`. It sets
   none of `management.endpoints.enabled-by-default`,
   `management.endpoints.access.default`, and
   `management.endpoint.luceneindex.access`. It puts `luceneindex` in the
   exposure list. An authenticated `GET`, `POST`, and `DELETE` all answer 404,
   and no request file appears.

   The shipped global default cannot isolate the annotation. Boot 4.0.8
   resolves both `NONE` and `UNRESTRICTED` to `NONE` when
   `enabled-by-default=false` is set. A review probe measured that on
   2026-10-06. `PropertiesEndpointAccessResolver` applies the global default
   first.
3. **Access and exposure, no credentials.** `GET`, `POST`, and `DELETE` answer
   401, and no request file appears.
4. **Access and exposure, actuator credentials.** `GET` answers 200 with six
   reports. `POST` writes `chat-rebuild.request`. `DELETE` writes
   `chat-drop.request`. Both answer `accepted=true`.
5. **Unknown name.** `accepted=false`, and the answer names the six indexes.
6. **Memory mode.** `POST` and `DELETE` answer `accepted=false`, and the reason
   names memory mode.

### Integration test, Redis

`RedisLuceneFilesRestartTests` starts against a Redis container with its own
node ids from `docs/NODEID-CLAIM.md`.

- Start, write, close, start again: `REUSED`, `documentsWritten == 0`, and the
  data is found.
- Write one entity to Redis with no index write, then start: `BUILT/MISMATCH`,
  and the entity is found.

### Mutations

| Mutation | Must fail |
|---|---|
| The compare reports equal for every entry | the different content test |
| The bitset check is removed | the compare-scan repeated key test |
| The pre-commit count check is removed | the build-scan repeated key test |
| Recovery deletes the request files | the drop request, failed build test |
| `defaultAccess` is `UNRESTRICTED` | endpoint test 2, which sets no global access default |
| `syncDirectory` ignores the `force` error | the directory sync force failure test |

Each mutation runs alone. Restore each file by its absolute path, and prove the
restore with `git status`.

### Gate

`build-health.sh --ci` exits 0. `docs/BUILD-HEALTH.md` and the test lists take
the new counts.

## Documents

- **`docs/ARCHITECTURE.md`** gains a section on the two modes, the layout, the
  start sequence, and the exactness condition. It points to `CHAT-lswjobhz`
  and `CHAT-oltrsgws`.
- **`docs/LUCENE-INDEX-FILES.md`** is the operator note. It states:
  - where the files live
  - the log lines and their reasons
  - the endpoint, and how to enable it
  - recovery: a restart, the two commands, and an offline delete of
    `<root>/<keyType>/<nodeId>/<index>` while the process is stopped
  - how to remove a request file by hand
  - this limit:

    > A node can retain a stale index after another node changes the shared
    > store. At restart, differing indexed content causes a rebuild.

- **`drift link`** binds the operator note to `LuceneIndex.kt`,
  `LuceneIndexLoad`, `LuceneIndexRegistry`, and `LuceneIndexEndpoint`. Review the
  prose before linking, as `AGENTS.md` requires.

## Open checks for the plan

- **A composition with a Lucene index and no local store. This is the first
  plan task.** Its behaviour must be settled before implementation starts. `LuceneIndexBeans`
  returns an empty load when no `PersistenceServiceBeans` bean exists. The plan
  must find each such composition. With no store, a start cannot validate files.
  The plan must state what `open` does there.
- **The cost of fsync per mutation.** Measure a Redis write with files against
  the same write without files. Report the numbers only.

## Not delivered by this work

- No deployment sets `app.index.lucene.root`.
- No live rebuild.
- No repair of `CHAT-lswjobhz` or `CHAT-oltrsgws`.
- No batched commits.
- No migration of `management-defaults.yml`.
