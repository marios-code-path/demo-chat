# Lucene Index Files Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task. `AGENTS.md` forbids sub-agent driven development. FP child issues track the tasks. The steps are numbered, and they carry no checklist.

**Goal:** Store each Lucene index in files under one configured root. A start reuses the files when they equal the store, and builds otherwise.

**Architecture:** A new package `com.demo.chat.index.lucene.storage` holds the storage modes, the canonical entry bytes, the commit header, the request files, and the start sequence. `LuceneIndex` gains an explicit lifecycle and a `SearcherManager`. `LuceneIndexBeans` wires one `LuceneIndexLoad` per index. A chat-core interface `IndexFileAdmin` lets the `chat-deploy` actuator endpoint reach the Lucene registry without a module cycle.

**Tech Stack:** Kotlin 2.4, Lucene 8.7.0, Spring Boot 4.0.8, Reactor, JUnit 5, AssertJ, Testcontainers Redis.

**Spec:** `docs/superpowers/specs/2026-10-06-lucene-index-files-design.md`, approved at `65a1af1a`.

**Issue:** `CHAT-ybtirmgj`. Branch `chat-ybtirmgj-lucene-files`.

## Global Constraints

- Root property: `app.index.lucene.root`. Absent selects memory mode. Blank fails the start.
- Layout: `<root>/<app.key.type>/<app.nodeid>/<index>`, index names `user`, `message`, `topic`, `membership`, `auth`, `keyvalue`.
- Lock and request file names: `chat-owner.lock`, `write.lock`, `chat-rebuild.request`, `chat-drop.request`. No file of this design starts with `_` or `segments`.
- Reserved document fields: `_key` and `_enc`.
- Canonical encoding format 1: int32 big-endian format version, then key length and UTF-8 bytes, then field count, then each field in encoder order as name length, name, value length, and value.
- Header keys: `chat.format`, `chat.lucene`, `chat.analyzer`, `chat.keyType`, `chat.nodeId`, `chat.index`.
- `StartOutcome.reason` is null for `REUSED` and never null for `BUILT`.
- The endpoint id is `luceneindex`, with `defaultAccess = Access.NONE`.
- Every runtime mutation commits. Close never commits.
- Do not use `IOUtils.fsync(directory, true)`. Use `syncDirectory`.
- Use Controlled English strict mode for every comment, KDoc, log message, error message, and commit message.
- Run Maven with one build per worktree. Write Maven output to a log file, and read only the exit code and the summary lines.

## Review Focus

1. **The root path is a regular file, or it cannot be written.** A reasonable operator expects the start to fail with the path in the message, and no file deleted. Task 5 pins this.
2. **The store is empty and the files hold documents**, for example after a Redis flush. The operator expects an empty index after the start, not the old documents. Task 5 pins this.
3. **UUID keys.** A UUID key text holds hyphens, and the exact `_key` term must still find exactly one document. Task 5 pins this.
4. **Multi-byte text in a field.** The length prefix counts UTF-8 bytes, not characters. Task 2 pins this.
5. **A query with bad syntax at run time.** The query fails, and the index stays `OPEN`. Task 6 pins this.

## Plan-level decisions

These decisions fill gaps that the spec leaves to the plan. The owner approves them with this plan.

1. **No-store behaviour.** Task 1 records the audit. The behaviour is:
   - Memory mode with no store: the index opens empty. The outcome is `BUILT/NO_COMMIT`, with 0 compared and 0 written. The load logs a warning.
   - Files mode with no store: the start fails before any lock or file access. The message names `app.index.lucene.root`.
   - A `RootKeySource.NONE` process runs no index load. Its indexes stay `NEW`, and every use fails with `is not open`. Only the authorization server is `NONE`, and it holds no Lucene index.
2. **`IndexFileAdmin` in chat-core.** `chat-deploy` does not depend on `chat-index-lucene`. So the endpoint depends on a chat-core interface, and `LuceneIndexRegistry` implements it.
3. **The endpoint condition.** `@ConditionalOnBean` on a scanned `@Component` depends on registration order. The endpoint uses the same property condition as `LuceneIndexBeans` instead, and it takes `ObjectProvider<IndexFileAdmin>`. With no admin bean it reports no index and refuses requests.
4. **HTTP endpoint tests live in `chat-deploy-memory`.** Only a module with both `chat-deploy` and `chat-index-lucene` can run a real request against the endpoint.
5. **Redis restart on one node id.** A Redis close does not release the node id claim (`CHAT-ocpojbyy`). The Redis test sets a 3 second claim TTL and retries the second start until the claim is free. Node ids 18 and 19 belong to that test.

## File Structure

**Create, `chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/`:**

| File | Responsibility |
|---|---|
| `EntryEncoding.kt` | `FORMAT`, the canonical bytes of one entry |
| `IndexEntry.kt` | One encoded entry: key text, bytes, and the Lucene document. `IndexFields` names the reserved fields. |
| `IndexHeader.kt` | The commit user data, read and write |
| `StartOutcome.kt` | `StartKind`, `BuildReason`, `StartOutcome`, and the log line |
| `Damage.kt` | The damage class of exceptions |
| `DirectorySync.kt` | `syncDirectory`, which propagates every failure |
| `IndexRequests.kt` | `IndexRequest`, and durable request file write, read, and clear |
| `LuceneStorage.kt` | `LuceneStorage`, `MemoryStorage`, `FileStorage`, and `LuceneStorages.of` |
| `IndexStartSequence.kt` | The start sequence: lock, decide, compare, recover, build |

**Create, other:**

| File | Responsibility |
|---|---|
| `chat-index-lucene/.../index/lucene/impl/IndexState.kt` | `NEW`, `OPEN`, `FAILED`, `CLOSED` |
| `chat-index-lucene/.../index/lucene/LuceneIndexLoad.kt` | The `StartupIndexLoad` for one Lucene index |
| `chat-index-lucene/.../index/lucene/LuceneIndexRegistry.kt` | Reports and requests for the six indexes |
| `chat-core/.../service/core/IndexFileAdmin.kt` | The admin interface and its report types |
| `chat-deploy/.../config/deploy/actuator/LuceneIndexEndpoint.kt` | The `luceneindex` endpoint |
| `docs/LUCENE-INDEX-FILES.md` | The operator note |

**Modify:** `LuceneIndex.kt`, `MembershipLuceneIndex.kt`, `KeyValueLuceneIndex.kt`, `LuceneIndexBeans.kt`, the seven Lucene test files, `docs/ARCHITECTURE.md`, `docs/NODEID-CLAIM.md`, `docs/BUILD-HEALTH.md`.

All paths below use these prefixes:

- `LMAIN` = `chat-index-lucene/src/main/kotlin/com/demo/chat`
- `LTEST` = `chat-index-lucene/src/test/kotlin/com/demo/chat/test`

## Task tracking

`FP_AGENTS.md` forbids markdown checklists. Each task has an FP child issue
under `CHAT-ybtirmgj`, and each issue depends on the issue of the task before
it.

| Task | Issue |
|---|---|
| 1 | `CHAT-vgsbrkxo` |
| 2 | `CHAT-fayymgrs` |
| 3 | `CHAT-gtzdjhze` |
| 4 | `CHAT-dyacywer` |
| 5 | `CHAT-didborwb` |
| 6 | `CHAT-nungovfh` |
| 7 | `CHAT-pfvbyyhx` |
| 8 | `CHAT-gnykibjr` |
| 9 | `CHAT-lztsrkev` |
| 10 | `CHAT-lnvtyvyv` |
| 11 | `CHAT-ndgafauz` |

At the start of a task, mark its issue `in-progress`. After its commit, add a
comment that names the commit and the measured test counts, then mark the
issue `done`.

## Build commands

Define once per shell:

```bash
cd /Users/darkbit1001/workspace/demo-chat
LOG=/private/tmp/claude-501/-Users-darkbit1001-workspace-demo-chat/lucene-files.log
lucene_test() { mvn -o -B -pl chat-core,chat-index-lucene -Dtest="$1" -Dsurefire.failIfNoSpecifiedTests=false test > "$LOG" 2>&1; echo "exit=$?"; grep -E "Tests run:|FAIL|ERROR\]" "$LOG" | tail -15; }
```

`-pl chat-core,chat-index-lucene` builds the chat-core test jar in the same run. Never run `-pl chat-index-lucene` alone. A single-module run resolves `chat-core` from `~/.m2`.

---

### Task 1: The no-store audit

**Issue:** `CHAT-vgsbrkxo`. Start with `fp issue update --status in-progress CHAT-vgsbrkxo`. End with a comment and `fp issue update --status done CHAT-vgsbrkxo`.

**Files:** none changed in the repository. The evidence goes to the issue.

The behaviour is in plan-level decision 1. This task records the evidence and measures the test contexts that meet the no-store branch today.

**Step 1: Record the static evidence**

```bash
cd /Users/darkbit1001/workspace/demo-chat
for m in chat-deploy-memory chat-deploy-redis chat-deploy-kafka; do echo "$m: $(grep -o '<artifactId>chat-persistence-[a-z]*</artifactId>' $m/pom.xml | sed 's/<[^>]*>//g' | sort -u | tr '\n' ' ')"; done
grep -rl --include=pom.xml "chat-index-lucene" . | grep -v target
```

Expected, measured on 2026-10-06:

```
chat-deploy-memory: chat-persistence-memory
chat-deploy-redis: chat-persistence-memory chat-persistence-redis chat-persistence-xstream
chat-deploy-kafka: chat-persistence-memory
```

and the poms `pom.xml`, `chat-deploy-kafka`, `chat-index-lucene`, `chat-deploy-redis`, `chat-deploy-memory`. `MemoryPersistenceServices` carries `matchIfMissing = true`. So every Lucene deployment has a store unless `app.service.core.persistence` names a module that is absent.

**Step 2: Measure the test contexts that meet the no-store branch**

Add one temporary line to `LuceneIndexBeans.load`, as the first statement:

```kotlin
if (persistence.ifAvailable == null) org.slf4j.LoggerFactory.getLogger("NO-STORE-PROBE").warn("NO-STORE-PROBE ${Thread.currentThread().stackTrace.firstOrNull { it.className.contains("Test") }}")
```

Run the default build and count:

```bash
mvn -o -B clean test -fae > "$LOG" 2>&1; echo "exit=$?"
grep -o "NO-STORE-PROBE.*" "$LOG" | sort | uniq -c
```

Expected: a list of test contexts, possibly empty. **None of them sets `app.index.lucene.root`**, because no code reads it yet. So each one takes the memory branch and keeps today's behaviour.

**Step 3: Remove the probe and prove the restore**

```bash
git checkout -- /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/main/kotlin/com/demo/chat/config/LuceneIndexBeans.kt
git status --short
```

Expected: no output from `git status --short`.

**Step 4: Record the result on the issue**

```bash
fp comment CHAT-ybtirmgj "No-store audit. Every Lucene deployment carries chat-persistence-memory, and MemoryPersistenceServices matches when the selector is missing. A store is absent only when app.service.core.persistence names an absent module, or in a test context. Probe count over the default build: <paste the uniq -c lines, or 'none'>. Behaviour per plan decision 1: memory mode opens empty with a warning, files mode fails the start, a NONE process opens no index."
```

---

### Task 2: Entry bytes, header, outcome, and damage class

**Issue:** `CHAT-fayymgrs`. Start with `fp issue update --status in-progress CHAT-fayymgrs`. End with a comment and `fp issue update --status done CHAT-fayymgrs`.

**Files:**
- Create: `LMAIN/index/lucene/storage/EntryEncoding.kt`, `IndexEntry.kt`, `IndexHeader.kt`, `StartOutcome.kt`, `Damage.kt`
- Test: `LTEST/index/lucene/storage/EntryEncodingTests.kt`, `IndexHeaderTests.kt`

**Interfaces:**
- Produces: `EntryEncoding.FORMAT: Int`, `EntryEncoding.encode(keyText: String, fields: List<Pair<String, String>>): ByteArray`
- Produces: `IndexFields.EXACT_KEY = "_key"`, `IndexFields.ENTRY = "_enc"`, `IndexFields.STORED_KEY = "key"`
- Produces: `class IndexEntry(val keyText: String, val bytes: ByteArray, val document: Document)` and `IndexEntry.of(keyText: String, fields: List<Pair<String, String>>): IndexEntry`
- Produces: `data class IndexHeader(format, lucene, analyzer, keyType, nodeId, index)`, `userData(): Map<String, String>`, `IndexHeader.read(Map<String, String>): IndexHeader?`, `IndexHeader.current(name: String, keyType: String, nodeId: String, analyzer: Analyzer): IndexHeader`
- Produces: `enum StartKind { REUSED, BUILT }`, `enum BuildReason { NO_COMMIT, HEADER, MISMATCH, DAMAGE, REQUESTED_REBUILD, REQUESTED_DROP }`, `data class StartOutcome(kind, reason: BuildReason?, entitiesCompared: Long, documentsWritten: Long)`, `StartOutcome.logLine(name: String): String`
- Produces: `object Damage { fun isDamage(e: Throwable): Boolean }`

**Step 1: Write the failing tests**

`LTEST/index/lucene/storage/EntryEncodingTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.EntryEncoding
import com.demo.chat.index.lucene.storage.IndexEntry
import com.demo.chat.index.lucene.storage.IndexFields
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EntryEncodingTests {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    /** Format 1 for key "7" with a repeated field name. The fields keep encoder order. */
    @Test
    fun `format 1 bytes are pinned`() {
        val bytes = EntryEncoding.encode("7", listOf("name" to "a", "name" to "b"))
        assertThat(hex(bytes)).isEqualTo(
            "00000001" + "00000001" + "37" + "00000002" +
                "00000004" + "6e616d65" + "00000001" + "61" +
                "00000004" + "6e616d65" + "00000001" + "62"
        )
    }

    /** The length prefix counts UTF-8 bytes. "é" is two bytes. */
    @Test
    fun `a multi-byte value carries its byte length`() {
        val bytes = EntryEncoding.encode("1", listOf("n" to "é"))
        assertThat(hex(bytes)).endsWith("00000001" + "6e" + "00000002" + "c3a9")
    }

    @Test
    fun `field order changes the bytes`() {
        val ab = EntryEncoding.encode("1", listOf("a" to "x", "b" to "y"))
        val ba = EntryEncoding.encode("1", listOf("b" to "y", "a" to "x"))
        assertThat(ab).isNotEqualTo(ba)
    }

    @Test
    fun `the entry document holds the reserved fields`() {
        val entry = IndexEntry.of("42", listOf("handle" to "h"))
        assertThat(entry.document.get(IndexFields.STORED_KEY)).isEqualTo("42")
        assertThat(entry.document.getBinaryValue(IndexFields.ENTRY).bytes).isEqualTo(entry.bytes)
        assertThat(entry.document.getField(IndexFields.EXACT_KEY).stringValue()).isEqualTo("42")
    }

    @Test
    fun `an encoder field cannot use a reserved name`() {
        assertThatThrownBy { IndexEntry.of("1", listOf(IndexFields.ENTRY to "x")) }
            .hasMessageContaining("'_enc'")
        assertThatThrownBy { IndexEntry.of("1", listOf(IndexFields.EXACT_KEY to "x")) }
            .hasMessageContaining("'_key'")
    }
}
```

`LTEST/index/lucene/storage/IndexHeaderTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.BuildReason
import com.demo.chat.index.lucene.storage.Damage
import com.demo.chat.index.lucene.storage.IndexHeader
import com.demo.chat.index.lucene.storage.StartKind
import com.demo.chat.index.lucene.storage.StartOutcome
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.CorruptIndexException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

class IndexHeaderTests {

    @Test
    fun `a header survives its user data`() {
        val header = IndexHeader.current("user", "long", "18", StandardAnalyzer())
        assertThat(IndexHeader.read(header.userData())).isEqualTo(header)
        assertThat(header.userData().keys).containsExactlyInAnyOrder(
            "chat.format", "chat.lucene", "chat.analyzer", "chat.keyType", "chat.nodeId", "chat.index"
        )
    }

    @Test
    fun `user data with a missing key reads as no header`() {
        assertThat(IndexHeader.read(mapOf("chat.format" to "1"))).isNull()
    }

    @Test
    fun `the log lines name the outcome`() {
        assertThat(StartOutcome(StartKind.REUSED, null, 42, 0).logLine("user"))
            .isEqualTo("lucene index user: reused, 42 entries compared, 0 written")
        assertThat(StartOutcome(StartKind.BUILT, BuildReason.MISMATCH, 3, 42).logLine("user"))
            .isEqualTo("lucene index user: built, reason=MISMATCH, 42 entries written")
    }

    @Test
    fun `damage and failure are separate classes`() {
        assertThat(Damage.isDamage(CorruptIndexException("x", "y"))).isTrue()
        assertThat(Damage.isDamage(EOFException())).isTrue()
        assertThat(Damage.isDamage(NoSuchFileException("_0.cfs"))).isTrue()
        assertThat(Damage.isDamage(AccessDeniedException("/root"))).isFalse()
        assertThat(Damage.isDamage(java.io.IOException("No space left on device"))).isFalse()
    }
}
```

**Step 2: Run the tests and see them fail**

Run: `lucene_test 'EntryEncodingTests,IndexHeaderTests'`
Expected: `exit=1`, with compile errors for the missing `com.demo.chat.index.lucene.storage` types.

**Step 3: Write the implementation**

`LMAIN/index/lucene/storage/EntryEncoding.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * The canonical bytes of one index entry. A start compares these bytes with
 * the `_enc` field of the stored document. See CHAT-ybtirmgj.
 */
object EntryEncoding {
    /**
     * The format version.
     *
     * Increase this value when the document layout changes, or when the
     * analyzer configuration changes. The entry bytes do not show an analyzer
     * change, so only this value and the header can show it.
     */
    const val FORMAT = 1

    /** All integers are int32, big-endian. Text is UTF-8 with a byte length prefix. */
    fun encode(keyText: String, fields: List<Pair<String, String>>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT)
            writeText(out, keyText)
            out.writeInt(fields.size)
            fields.forEach { (name, value) ->
                writeText(out, name)
                writeText(out, value)
            }
        }
        return bytes.toByteArray()
    }

    private fun writeText(out: DataOutputStream, text: String) {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        out.writeInt(utf8.size)
        out.write(utf8)
    }
}
```

`LMAIN/index/lucene/storage/IndexEntry.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

import com.demo.chat.domain.ChatException
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField

/** The field names that the index writes itself. */
object IndexFields {
    /** The key text that a query returns. Stored and analyzed. */
    const val STORED_KEY = "key"

    /** The exact key. Not analyzed. Replacement and lookup match it. */
    const val EXACT_KEY = "_key"

    /** The canonical entry bytes. Stored, not indexed. */
    const val ENTRY = "_enc"

    val RESERVED = setOf(EXACT_KEY, ENTRY)
}

/**
 * One encoded entry. The document and the bytes come from one field list, so
 * the bytes always describe what Lucene indexed.
 */
class IndexEntry(val keyText: String, val bytes: ByteArray, val document: Document) {
    companion object {
        fun of(keyText: String, fields: List<Pair<String, String>>): IndexEntry {
            requireNoReservedField(fields)
            val bytes = EntryEncoding.encode(keyText, fields)
            val document = Document().apply {
                fields.forEach { (name, value) -> add(Field(name, value, TextField.TYPE_NOT_STORED)) }
                add(Field(IndexFields.STORED_KEY, keyText, TextField.TYPE_STORED))
                add(StringField(IndexFields.EXACT_KEY, keyText, Field.Store.NO))
                add(StoredField(IndexFields.ENTRY, bytes))
            }
            return IndexEntry(keyText, bytes, document)
        }

        /**
         * Rejects an encoded field that carries a reserved name.
         *
         * Lucene refuses a document that gives one name two index options. The
         * check runs before any writer change, so a refusal cannot lose an entry.
         */
        fun requireNoReservedField(fields: List<Pair<String, String>>) {
            fields.firstOrNull { it.first in IndexFields.RESERVED }?.let { field ->
                throw ChatException(
                    "An index field cannot use the name '${field.first}'. The index writes that field itself."
                )
            }
        }
    }
}
```

`IndexEntry.of` throws `ChatException`, which is a checked Java exception. Kotlin does not require a declaration. Reactor reports it as an error signal.

`LMAIN/index/lucene/storage/IndexHeader.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.util.Version

/** The commit user data of one index. A start that reads a different header builds. */
data class IndexHeader(
    val format: String,
    val lucene: String,
    val analyzer: String,
    val keyType: String,
    val nodeId: String,
    val index: String,
) {
    fun userData(): Map<String, String> = mapOf(
        FORMAT to format, LUCENE to lucene, ANALYZER to analyzer,
        KEY_TYPE to keyType, NODE_ID to nodeId, INDEX to index,
    )

    companion object {
        const val FORMAT = "chat.format"
        const val LUCENE = "chat.lucene"
        const val ANALYZER = "chat.analyzer"
        const val KEY_TYPE = "chat.keyType"
        const val NODE_ID = "chat.nodeId"
        const val INDEX = "chat.index"

        fun current(name: String, keyType: String, nodeId: String, analyzer: Analyzer) = IndexHeader(
            EntryEncoding.FORMAT.toString(), Version.LATEST.toString(), analyzer.javaClass.name, keyType, nodeId, name,
        )

        /** Returns null when any key is missing. A missing key is a different header. */
        fun read(data: Map<String, String>): IndexHeader? {
            return IndexHeader(
                data[FORMAT] ?: return null,
                data[LUCENE] ?: return null,
                data[ANALYZER] ?: return null,
                data[KEY_TYPE] ?: return null,
                data[NODE_ID] ?: return null,
                data[INDEX] ?: return null,
            )
        }
    }
}
```

`LMAIN/index/lucene/storage/StartOutcome.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

enum class StartKind { REUSED, BUILT }

enum class BuildReason { NO_COMMIT, HEADER, MISMATCH, DAMAGE, REQUESTED_REBUILD, REQUESTED_DROP }

/** The result of one start. [reason] is null for REUSED and never null for BUILT. */
data class StartOutcome(
    val kind: StartKind,
    val reason: BuildReason?,
    val entitiesCompared: Long,
    val documentsWritten: Long,
) {
    init {
        require((kind == StartKind.REUSED) == (reason == null)) {
            "A REUSED outcome has no reason, and a BUILT outcome has one. Received $kind with $reason."
        }
    }

    fun logLine(name: String): String = when (kind) {
        StartKind.REUSED -> "lucene index $name: reused, $entitiesCompared entries compared, $documentsWritten written"
        StartKind.BUILT -> "lucene index $name: built, reason=$reason, $documentsWritten entries written"
    }
}
```

`LMAIN/index/lucene/storage/Damage.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

import org.apache.lucene.index.CorruptIndexException
import org.apache.lucene.index.IndexFormatTooNewException
import org.apache.lucene.index.IndexFormatTooOldException
import java.io.EOFException
import java.nio.file.NoSuchFileException

/**
 * The exceptions that mean damaged index files. A start recovers from them.
 * Every other exception fails the start, for example a refused permission or
 * a full disk. See the spec section "The damage class".
 */
object Damage {
    fun isDamage(e: Throwable): Boolean =
        e is CorruptIndexException ||
            e is IndexFormatTooOldException ||
            e is IndexFormatTooNewException ||
            e is EOFException ||
            e is NoSuchFileException
}
```

**Step 4: Run the tests and see them pass**

Run: `lucene_test 'EntryEncodingTests,IndexHeaderTests'`
Expected: `exit=0`, and `Tests run: 9, Failures: 0, Errors: 0`.

**Step 5: Commit**

```bash
git add \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/EntryEncoding.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexEntry.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexHeader.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/StartOutcome.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/Damage.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/EntryEncodingTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/IndexHeaderTests.kt
git commit -m "Add the Lucene entry bytes, header and outcome (CHAT-ybtirmgj)

Format 1 encodes the key and the fields in encoder order, with UTF-8
byte lengths. The header holds six values in the commit user data. The
damage class separates recovery from a failed start.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Directory sync and request files

**Issue:** `CHAT-gtzdjhze`. Start with `fp issue update --status in-progress CHAT-gtzdjhze`. End with a comment and `fp issue update --status done CHAT-gtzdjhze`.

**Files:**
- Create: `LMAIN/index/lucene/storage/DirectorySync.kt`, `IndexRequests.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/core/IndexFileAdmin.kt`
- Test: `LTEST/index/lucene/storage/DirectorySyncTests.kt`, `IndexRequestsTests.kt`

**Interfaces:**
- Produces: `fun syncDirectory(dir: Path, opener: (Path) -> FileChannel = { FileChannel.open(it, StandardOpenOption.READ) })`
- Produces: `enum class IndexRequest(val fileName: String) { REBUILD("chat-rebuild.request"), DROP("chat-drop.request") }`
- Produces: `class IndexRequests(dir: Path, sync: (Path) -> Unit = ::syncDirectory, delete: (Path) -> Unit = { Files.deleteIfExists(it) })` with `removeStaleTemporary()`, `pending(): IndexRequest?`, `write(request: IndexRequest): IndexRequestResult`, `clear(requests: List<IndexRequest>)`, and `IndexRequests.clearedBy(pending: IndexRequest?): List<IndexRequest>`
- Produces, chat-core: `data class IndexRequestResult(val accepted: Boolean, val reason: String, val path: String?)`, `data class IndexFileReport(...)`, `interface IndexFileAdmin`

**Step 1: Write the failing tests**

`LTEST/index/lucene/storage/DirectorySyncTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.syncDirectory
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class DirectorySyncTests {

    @Test
    fun `a real directory syncs`(@TempDir dir: Path) {
        assertThatCode { syncDirectory(dir) }.doesNotThrowAnyException()
    }

    @Test
    fun `an open failure reaches the caller`(@TempDir dir: Path) {
        assertThatThrownBy { syncDirectory(dir.resolve("absent")) }.isInstanceOf(IOException::class.java)
    }

    /** The channel opens normally, and force fails. The real helper must report the failure. */
    @Test
    fun `a force failure reaches the caller`(@TempDir dir: Path) {
        val opener = { path: Path -> ForceFailingChannel(FileChannel.open(path, StandardOpenOption.READ)) }
        assertThatThrownBy { syncDirectory(dir, opener) }
            .isInstanceOf(IOException::class.java)
            .hasMessage("injected force failure")
    }
}

/** A channel that delegates every call except force, which fails. */
class ForceFailingChannel(private val delegate: FileChannel) : FileChannel() {
    override fun force(metaData: Boolean): Unit = throw IOException("injected force failure")
    override fun read(dst: ByteBuffer): Int = delegate.read(dst)
    override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long = delegate.read(dsts, offset, length)
    override fun write(src: ByteBuffer): Int = delegate.write(src)
    override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long = delegate.write(srcs, offset, length)
    override fun position(): Long = delegate.position()
    override fun position(newPosition: Long): FileChannel = apply { delegate.position(newPosition) }
    override fun size(): Long = delegate.size()
    override fun truncate(size: Long): FileChannel = apply { delegate.truncate(size) }
    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long = delegate.transferTo(position, count, target)
    override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long = delegate.transferFrom(src, position, count)
    override fun read(dst: ByteBuffer, position: Long): Int = delegate.read(dst, position)
    override fun write(src: ByteBuffer, position: Long): Int = delegate.write(src, position)
    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer = delegate.map(mode, position, size)
    override fun lock(position: Long, size: Long, shared: Boolean): FileLock = delegate.lock(position, size, shared)
    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? = delegate.tryLock(position, size, shared)
    override fun implCloseChannel() = delegate.close()
}
```

`LTEST/index/lucene/storage/IndexRequestsTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.IndexRequest
import com.demo.chat.index.lucene.storage.IndexRequests
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class IndexRequestsTests {

    @Test
    fun `a written request is pending and durable`(@TempDir dir: Path) {
        val result = IndexRequests(dir).write(IndexRequest.REBUILD)
        assertThat(result.accepted).isTrue()
        assertThat(result.path).isEqualTo(dir.resolve("chat-rebuild.request").toString())
        assertThat(IndexRequests(dir).pending()).isEqualTo(IndexRequest.REBUILD)
        assertThat(Files.list(dir).map { it.fileName.toString() }.toList()).containsExactly("chat-rebuild.request")
    }

    @Test
    fun `a drop request takes precedence`(@TempDir dir: Path) {
        val requests = IndexRequests(dir)
        requests.write(IndexRequest.REBUILD)
        requests.write(IndexRequest.DROP)
        assertThat(requests.pending()).isEqualTo(IndexRequest.DROP)
    }

    @Test
    fun `a stale temporary file is removed and never counts`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("chat-drop.request.tmp"), "DROP")
        val requests = IndexRequests(dir)
        assertThat(requests.pending()).isNull()
        requests.removeStaleTemporary()
        assertThat(Files.exists(dir.resolve("chat-drop.request.tmp"))).isFalse()
    }

    /** The move succeeded, so the answer must say that the request can remain pending. */
    @Test
    fun `a directory sync failure after the move reports a pending request`(@TempDir dir: Path) {
        val requests = IndexRequests(dir, sync = { throw IOException("injected sync failure") })
        val result = requests.write(IndexRequest.DROP)
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("can remain pending").contains("injected sync failure")
        assertThat(result.path).isEqualTo(dir.resolve("chat-drop.request").toString())
        assertThat(Files.exists(dir.resolve("chat-drop.request"))).isTrue()
    }

    @Test
    fun `a failure before the move keeps an earlier request`(@TempDir dir: Path) {
        IndexRequests(dir).write(IndexRequest.REBUILD)
        Files.createDirectory(dir.resolve("chat-drop.request.tmp"))
        val result = IndexRequests(dir).write(IndexRequest.DROP)
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("This attempt installs no new request. Any previously pending request remains.")
        assertThat(IndexRequests(dir).pending()).isEqualTo(IndexRequest.REBUILD)
    }

    @Test
    fun `clear deletes the requests and syncs`(@TempDir dir: Path) {
        var synced = 0
        val requests = IndexRequests(dir, sync = { synced++ })
        requests.write(IndexRequest.REBUILD)
        requests.write(IndexRequest.DROP)
        requests.clear(IndexRequests.clearedBy(IndexRequest.DROP))
        assertThat(requests.pending()).isNull()
        assertThat(synced).isEqualTo(3)
    }

    @Test
    fun `a drop clears both, and a rebuild clears only itself`() {
        assertThat(IndexRequests.clearedBy(IndexRequest.DROP)).containsExactly(IndexRequest.DROP, IndexRequest.REBUILD)
        assertThat(IndexRequests.clearedBy(IndexRequest.REBUILD)).containsExactly(IndexRequest.REBUILD)
        assertThat(IndexRequests.clearedBy(null)).isEmpty()
    }

    @Test
    fun `a delete failure reaches the caller`(@TempDir dir: Path) {
        val requests = IndexRequests(dir, delete = { throw IOException("injected delete failure") })
        requests.write(IndexRequest.REBUILD)
        assertThatThrownBy { requests.clear(listOf(IndexRequest.REBUILD)) }.hasMessage("injected delete failure")
    }
}
```

The "failure before the move" test blocks the temporary path with a directory. `FileChannel.open` with `WRITE` on a directory fails before any move.

**Step 2: Run the tests and see them fail**

Run: `lucene_test 'DirectorySyncTests,IndexRequestsTests'`
Expected: `exit=1`, with compile errors for `syncDirectory`, `IndexRequests`, and `IndexRequestResult`.

**Step 3: Write the implementation**

`chat-core/src/main/kotlin/com/demo/chat/service/core/IndexFileAdmin.kt`:

```kotlin
package com.demo.chat.service.core

/** The result of one operator request. [path] names the request file when one exists. */
data class IndexRequestResult(val accepted: Boolean, val reason: String, val path: String?)

/** The state of one file-backed index. Every value is text or a number, so no index library type leaks. */
data class IndexFileReport(
    val name: String,
    val mode: String,
    val path: String?,
    val state: String,
    val liveDocuments: Int?,
    val outcome: String?,
    val reason: String?,
    val entitiesCompared: Long?,
    val documentsWritten: Long?,
    val pendingRequest: String?,
)

/**
 * The operator view of the file-backed indexes. The Lucene module implements
 * it, and the actuator endpoint reads it. See CHAT-ybtirmgj.
 */
interface IndexFileAdmin {
    fun reports(): List<IndexFileReport>
    fun requestRebuild(name: String): IndexRequestResult
    fun requestDrop(name: String): IndexRequestResult
}
```

`LMAIN/index/lucene/storage/DirectorySync.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Syncs the metadata of one directory, and reports every failure.
 *
 * Do not use `IOUtils.fsync(directory, true)` in its place. Lucene 8.7
 * suppresses an IOException from force on a directory, so that call cannot
 * prove that a request file is durable. The opener is a parameter, so a test
 * can pass a channel whose force fails and still run this body.
 */
fun syncDirectory(
    dir: Path,
    opener: (Path) -> FileChannel = { FileChannel.open(it, StandardOpenOption.READ) },
) {
    opener(dir).use { it.force(true) }
}
```

`LMAIN/index/lucene/storage/IndexRequests.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

import com.demo.chat.service.core.IndexRequestResult
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** An operator request. Neither name starts with "_" or "segments", so Lucene never deletes it. */
enum class IndexRequest(val fileName: String) {
    REBUILD("chat-rebuild.request"),
    DROP("chat-drop.request"),
}

/**
 * The request files of one index directory. Only the start sequence acts on a
 * request. See the spec section "Request durability".
 */
class IndexRequests(
    private val dir: Path,
    private val sync: (Path) -> Unit = { syncDirectory(it) },
    private val delete: (Path) -> Unit = { Files.deleteIfExists(it) },
) {

    /** A crashed command can leave a temporary file. It never counts as a request. */
    fun removeStaleTemporary() {
        IndexRequest.entries.forEach { Files.deleteIfExists(temporary(it)) }
    }

    /** A drop request takes precedence when both files exist. */
    fun pending(): IndexRequest? = when {
        Files.exists(dir.resolve(IndexRequest.DROP.fileName)) -> IndexRequest.DROP
        Files.exists(dir.resolve(IndexRequest.REBUILD.fileName)) -> IndexRequest.REBUILD
        else -> null
    }

    fun write(request: IndexRequest): IndexRequestResult {
        val temporary = temporary(request)
        val target = dir.resolve(request.fileName)
        try {
            FileChannel.open(
                temporary, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
            ).use { channel ->
                channel.write(ByteBuffer.wrap(request.name.toByteArray(Charsets.UTF_8)))
                channel.force(true)
            }
        } catch (e: IOException) {
            return refusedBeforeMove("The request file could not be written: ${e.message}.")
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.deleteIfExists(temporary)
            return refusedBeforeMove("The file system refused an atomic move: ${e.message}.")
        } catch (e: IOException) {
            Files.deleteIfExists(temporary)
            return refusedBeforeMove("The request file could not be moved into place: ${e.message}.")
        }
        try {
            sync(dir)
        } catch (e: IOException) {
            return IndexRequestResult(
                false,
                "The request file is in place, and the directory sync failed: ${e.message}. " +
                    "The request can remain pending, and the next start can act on it.",
                target.toString(),
            )
        }
        return IndexRequestResult(true, "The next start acts on this request.", target.toString())
    }

    /** Deletes the named requests, then syncs the directory. Any failure reaches the caller. */
    fun clear(requests: List<IndexRequest>) {
        if (requests.isEmpty()) return
        requests.forEach { delete(dir.resolve(it.fileName)) }
        sync(dir)
    }

    private fun temporary(request: IndexRequest): Path = dir.resolve(request.fileName + ".tmp")

    private fun refusedBeforeMove(cause: String) = IndexRequestResult(
        false, "$cause This attempt installs no new request. Any previously pending request remains.", null,
    )

    companion object {
        /** A drop supersedes a rebuild, so a drop build clears both. */
        fun clearedBy(pending: IndexRequest?): List<IndexRequest> = when (pending) {
            IndexRequest.DROP -> listOf(IndexRequest.DROP, IndexRequest.REBUILD)
            IndexRequest.REBUILD -> listOf(IndexRequest.REBUILD)
            null -> emptyList()
        }
    }
}
```

The `clear` test expects 3 syncs: two from `write` and one from `clear`.

**Step 4: Run the tests and see them pass**

Run: `lucene_test 'DirectorySyncTests,IndexRequestsTests'`
Expected: `exit=0`, and `Tests run: 11, Failures: 0, Errors: 0`.

**Step 5: Run the sync mutation**

Stage the new files first, so that a checkout can restore the staged version:

```bash
git add \
  /Users/darkbit1001/workspace/demo-chat/chat-core/src/main/kotlin/com/demo/chat/service/core/IndexFileAdmin.kt \
  /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/DirectorySync.kt \
  /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexRequests.kt \
  /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/DirectorySyncTests.kt \
  /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/IndexRequestsTests.kt
```

Edit `DirectorySync.kt`: replace `opener(dir).use { it.force(true) }` with `opener(dir).use { runCatching { it.force(true) } }`.
Run: `lucene_test 'DirectorySyncTests'`
Expected: `exit=1`, and `a force failure reaches the caller` fails.
Restore by absolute path, and prove it:

```bash
git checkout -- /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/DirectorySync.kt
git diff --stat
```

Expected: `git diff --stat` prints nothing.

**Step 6: Commit**

```bash
git add \
  chat-core/src/main/kotlin/com/demo/chat/service/core/IndexFileAdmin.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/DirectorySync.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexRequests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/DirectorySyncTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/IndexRequestsTests.kt
git commit -m "Add durable Lucene index request files (CHAT-ybtirmgj)

A request file is written to a temporary name, synced, moved
atomically, and the directory is synced. syncDirectory reports every
failure, because Lucene IOUtils.fsync suppresses a directory force
error. IndexFileAdmin lets the actuator endpoint read the Lucene
registry without a module cycle.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Storage modes

**Issue:** `CHAT-dyacywer`. Start with `fp issue update --status in-progress CHAT-dyacywer`. End with a comment and `fp issue update --status done CHAT-dyacywer`.

**Files:**
- Create: `LMAIN/index/lucene/storage/LuceneStorage.kt`
- Test: `LTEST/index/lucene/storage/LuceneStorageTests.kt`

**Interfaces:**
- Consumes: `IndexRequests` from Task 3.
- Produces:

```kotlin
interface LuceneStorage {
    val mode: String            // "memory" or "files"
    val persistent: Boolean
    val keyType: String
    val nodeId: String
    val summary: String         // "memory" or "files at <root>"
    fun open(name: String): Directory
    fun path(name: String): Path?
    fun requests(name: String): IndexRequests?
    fun sync(name: String)
    fun describe(name: String): String
}
class MemoryStorage : LuceneStorage
open class FileStorage(root: Path, keyType: String, nodeId: Int) : LuceneStorage
object LuceneStorages { fun of(root: String?, keyType: String?, nodeId: Int?): LuceneStorage }
```

**Step 1: Write the failing tests**

`LTEST/index/lucene/storage/LuceneStorageTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.LuceneStorages
import com.demo.chat.index.lucene.storage.MemoryStorage
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.FSDirectory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LuceneStorageTests {

    @Test
    fun `no root selects memory`() {
        val storage = LuceneStorages.of(null, null, null)
        assertThat(storage).isInstanceOf(MemoryStorage::class.java)
        assertThat(storage.summary).isEqualTo("memory")
        assertThat(storage.open("user")).isInstanceOf(ByteBuffersDirectory::class.java)
        assertThat(storage.requests("user")).isNull()
    }

    @Test
    fun `a blank root fails and names the property`() {
        assertThatThrownBy { LuceneStorages.of("  ", "long", 1) }.hasMessageContaining("app.index.lucene.root")
    }

    @Test
    fun `a root needs the key type and the node id`() {
        assertThatThrownBy { LuceneStorages.of("/tmp/x", null, 1) }.hasMessageContaining("app.key.type")
        assertThatThrownBy { LuceneStorages.of("/tmp/x", "long", null) }.hasMessageContaining("app.nodeid")
    }

    @Test
    fun `the path separates the key type and the node id`(@TempDir root: Path) {
        val one = LuceneStorages.of(root.toString(), "long", 1)
        val two = LuceneStorages.of(root.toString(), "long", 2)
        assertThat(one).isInstanceOf(FileStorage::class.java)
        assertThat(one.path("user")).isEqualTo(root.resolve("long").resolve("1").resolve("user"))
        assertThat(two.path("user")).isEqualTo(root.resolve("long").resolve("2").resolve("user"))
        assertThat(one.summary).isEqualTo("files at $root")
        one.open("user").use { assertThat(it).isInstanceOf(FSDirectory::class.java) }
        assertThat(one.path("user")!!.toFile().isDirectory).isTrue()
        one.sync("user")
    }

    @Test
    fun `memory mode has nothing to sync`() {
        MemoryStorage().sync("user")
    }
}
```

**Step 2: Run the tests and see them fail**

Run: `lucene_test 'LuceneStorageTests'`
Expected: `exit=1`, with compile errors for `LuceneStorages`.

**Step 3: Write the implementation**

`LMAIN/index/lucene/storage/LuceneStorage.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

import com.demo.chat.domain.ChatException
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NativeFSLockFactory
import java.nio.file.Files
import java.nio.file.Path

/** Where the Lucene indexes of one process live. See CHAT-ybtirmgj. */
interface LuceneStorage {
    val mode: String
    val persistent: Boolean
    val keyType: String
    val nodeId: String
    val summary: String
    fun open(name: String): Directory
    fun path(name: String): Path?
    fun requests(name: String): IndexRequests?

    /** Syncs the index directory. Memory mode has nothing to sync. Any failure reaches the caller. */
    fun sync(name: String)

    fun describe(name: String): String = path(name)?.toString() ?: "memory"
}

/** Each index lives in process memory, and each start builds it. */
class MemoryStorage : LuceneStorage {
    override val mode = "memory"
    override val persistent = false
    override val keyType = "-"
    override val nodeId = "-"
    override val summary = "memory"
    override fun open(name: String): Directory = ByteBuffersDirectory()
    override fun path(name: String): Path? = null
    override fun requests(name: String): IndexRequests? = null
    override fun sync(name: String) = Unit
}

/**
 * Each index lives in `<root>/<keyType>/<nodeId>/<index>`. Open so that a test
 * can replace the request files or the directory.
 */
open class FileStorage(private val root: Path, keyType: String, nodeId: Int) : LuceneStorage {
    override val mode = "files"
    override val persistent = true
    override val keyType = keyType
    override val nodeId = nodeId.toString()
    override val summary = "files at $root"

    override fun path(name: String): Path = root.resolve(keyType).resolve(nodeId.toString()).resolve(name)

    override fun open(name: String): Directory {
        val dir = Files.createDirectories(path(name))
        return FSDirectory.open(dir, NativeFSLockFactory.INSTANCE)
    }

    override fun requests(name: String): IndexRequests? = IndexRequests(path(name))

    override fun sync(name: String) = syncDirectory(path(name))
}

object LuceneStorages {
    const val ROOT = "app.index.lucene.root"

    fun of(root: String?, keyType: String?, nodeId: Int?): LuceneStorage {
        if (root == null) return MemoryStorage()
        if (root.isBlank()) {
            throw ChatException("$ROOT is blank. Remove it to keep the Lucene indexes in memory, or set a directory.")
        }
        val type = keyType ?: throw ChatException("$ROOT is set, and app.key.type is not. The index path needs both.")
        val node = nodeId ?: throw ChatException("$ROOT is set, and app.nodeid is not. The index path needs both.")
        return FileStorage(Path.of(root), type, node)
    }
}
```

**Step 4: Run the tests and see them pass**

Run: `lucene_test 'LuceneStorageTests'`
Expected: `exit=0`, and `Tests run: 5, Failures: 0, Errors: 0`.

**Step 5: Commit**

```bash
git add \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/LuceneStorage.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/LuceneStorageTests.kt
git commit -m "Add the Lucene storage modes (CHAT-ybtirmgj)

No root keeps every index in memory. A root places each index under
the key type and the node id. A blank root fails the start.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The start sequence

**Issue:** `CHAT-didborwb`. Start with `fp issue update --status in-progress CHAT-didborwb`. End with a comment and `fp issue update --status done CHAT-didborwb`.

**Files:**
- Create: `LMAIN/index/lucene/storage/IndexStartSequence.kt`
- Test: `LTEST/index/lucene/storage/IndexStartSequenceTests.kt`, `LTEST/index/lucene/storage/StartFixtures.kt`

**Interfaces:**
- Consumes: Tasks 2, 3 and 4.
- Produces:

```kotlin
class Started(val directory: Directory, val ownerLock: Lock, val writer: IndexWriter, val manager: SearcherManager, val outcome: StartOutcome)
class IndexStartSequence<E> internal constructor(
    name: String,
    storage: LuceneStorage,
    analyzer: Analyzer,
    entryOf: (E) -> IndexEntry,
    entities: (() -> Flux<out E>)?,
    rollback: (IndexWriter) -> Unit,      // a test seam for recovery
) {
    constructor(name, storage, analyzer, entryOf, entities)   // rollback = IndexWriter::rollback
    fun run(): Started
}
const val OWNER_LOCK = "chat-owner.lock"   // in IndexStartSequence.kt
```

**Step 1: Write the fixtures**

`LTEST/index/lucene/storage/StartFixtures.kt`:

```kotlin
package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.IndexEntry
import com.demo.chat.index.lucene.storage.IndexStartSequence
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.index.lucene.storage.Started
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import reactor.core.publisher.Flux
import java.io.IOException

/** One stored entity of the tests: a key text and one field. */
data class Row(val key: String, val name: String)

fun encode(row: Row): IndexEntry = IndexEntry.of(row.key, listOf("name" to row.name))

/**
 * A store stub. It counts each scan. A scan that matches [failOnScan] fails at
 * once.
 *
 * A store error cannot be placed after a chosen row. Reactor 3.8.7
 * `BlockingIterable` reports a terminal error as soon as it arrives, even while
 * rows are still queued. So a test that needs a failure after a writer update
 * uses [FailingEncoder].
 */
class RowStore(var rows: List<Row>, private val failOnScan: Int? = null) {
    var scans = 0
        private set

    fun all(): Flux<Row> = Flux.defer {
        scans++
        if (scans == failOnScan) Flux.error(IOException("injected store failure")) else Flux.fromIterable(rows)
    }
}

/**
 * An encoder that fails on one key during one scan. It counts the rows that it
 * encoded in that scan before the failure. A row that the build encoded is
 * also written to the writer before the next row is read.
 */
class FailingEncoder(private val store: RowStore, private val failKey: String, private val onScan: Int) {
    var encodedInFailingScan = 0
        private set

    fun entryOf(row: Row): IndexEntry {
        if (store.scans == onScan) {
            if (row.key == failKey) throw IllegalStateException("injected encoder failure")
            encodedInFailingScan++
        }
        return encode(row)
    }
}

fun start(
    storage: LuceneStorage,
    store: RowStore?,
    name: String = "user",
    entry: (Row) -> IndexEntry = ::encode,
    rollback: (IndexWriter) -> Unit = { it.rollback() },
): Started =
    IndexStartSequence(name, storage, StandardAnalyzer(), entry, store?.let { s -> { s.all() } }, rollback).run()

fun Started.closeAll() {
    manager.close()
    writer.close()
    directory.close()
    ownerLock.close()
}

fun Started.holds(key: String): Boolean = DirectoryReader.open(directory).use { reader ->
    IndexSearcher(reader).search(TermQuery(Term("_key", key)), 2).scoreDocs.size == 1
}
```

**Step 2: Write the failing tests**

`LTEST/index/lucene/storage/IndexStartSequenceTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.BuildReason
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.IndexHeader
import com.demo.chat.index.lucene.storage.IndexRequest
import com.demo.chat.index.lucene.storage.IndexRequests
import com.demo.chat.index.lucene.storage.MemoryStorage
import com.demo.chat.index.lucene.storage.OWNER_LOCK
import com.demo.chat.index.lucene.storage.StartKind
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.FilterDirectory
import org.apache.lucene.store.Lock
import org.apache.lucene.store.NativeFSLockFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

class IndexStartSequenceTests {

    private val a = Row("1", "alpha")
    private val b = Row("2", "beta")

    private fun files(root: Path, node: Int = 1) = FileStorage(root, "long", node)
    private fun dir(root: Path, node: Int = 1) = root.resolve("long").resolve(node.toString()).resolve("user")
    private fun names(root: Path) = Files.list(dir(root)).map { it.fileName.toString() }.toList().toSet()

    /** Builds the first commit, then closes. */
    private fun seed(root: Path, vararg rows: Row) = start(files(root), RowStore(rows.toList())).closeAll()

    @Test
    fun `memory mode builds from the store`() {
        val started = start(MemoryStorage(), RowStore(listOf(a, b)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.BUILT)
        assertThat(started.outcome.reason).isEqualTo(BuildReason.NO_COMMIT)
        assertThat(started.outcome.documentsWritten).isEqualTo(2)
        started.closeAll()
    }

    @Test
    fun `memory mode with no store opens empty`() {
        val started = start(MemoryStorage(), null)
        assertThat(started.outcome.reason).isEqualTo(BuildReason.NO_COMMIT)
        assertThat(started.outcome.documentsWritten).isEqualTo(0)
        started.closeAll()
    }

    @Test
    fun `files mode with no store fails before any file access`(@TempDir root: Path) {
        assertThatThrownBy { start(files(root), null) }.hasMessageContaining("app.index.lucene.root")
        assertThat(Files.exists(root.resolve("long"))).isFalse()
    }

    @Test
    fun `intact files are reused and nothing is written`(@TempDir root: Path) {
        seed(root, a, b)
        val started = start(files(root), RowStore(listOf(a, b)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.REUSED)
        assertThat(started.outcome.reason).isNull()
        assertThat(started.outcome.entitiesCompared).isEqualTo(2)
        assertThat(started.outcome.documentsWritten).isEqualTo(0)
        assertThat(started.holds("1")).isTrue()
        started.closeAll()
    }

    @Test
    fun `missing files build`(@TempDir root: Path) {
        seed(root, a)
        dir(root).toFile().deleteRecursively()
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.NO_COMMIT)
        assertThat(started.holds("1")).isTrue()
        started.closeAll()
    }

    @Test
    fun `damaged files recover and build`(@TempDir root: Path) {
        seed(root, a, b)
        val segment = Files.list(dir(root)).filter { path ->
            val n = path.fileName.toString()
            n.startsWith("_") && !n.endsWith(".si")
        }.max(compareBy { Files.size(it) }).get()
        RandomAccessFile(segment.toFile(), "rw").use { file ->
            file.seek(file.length() / 2)
            file.write(ByteArray(16) { 0x5A })
        }
        val started = start(files(root), RowStore(listOf(a, b)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.DAMAGE)
        assertThat(started.holds("1")).isTrue()
        assertThat(started.holds("2")).isTrue()
        started.closeAll()
    }

    /** A store write with no index write, which a crash between the two leaves. */
    @Test
    fun `different content builds`(@TempDir root: Path) {
        seed(root, a)
        val started = start(files(root), RowStore(listOf(a, b)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.MISMATCH)
        assertThat(started.holds("2")).isTrue()
        started.closeAll()
    }

    @Test
    fun `a changed field builds`(@TempDir root: Path) {
        seed(root, a)
        val started = start(files(root), RowStore(listOf(a.copy(name = "changed"))))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.MISMATCH)
        started.closeAll()
    }

    /** Review focus 2: a flushed store must empty the index. */
    @Test
    fun `an empty store empties the index`(@TempDir root: Path) {
        seed(root, a, b)
        val started = start(files(root), RowStore(emptyList()))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.MISMATCH)
        assertThat(started.holds("1")).isFalse()
        started.closeAll()
    }

    /** Review focus 3: a UUID key text holds hyphens. */
    @Test
    fun `uuid keys are reused`(@TempDir root: Path) {
        val u1 = Row("550e8400-e29b-41d4-a716-446655440000", "one")
        val u2 = Row("550e8400-0000-0000-0000-000000000000", "two")
        seed(root, u1, u2)
        val started = start(files(root), RowStore(listOf(u1, u2)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.REUSED)
        started.closeAll()
    }

    @Test
    fun `a header change builds`(@TempDir root: Path) {
        FSDirectory.open(dir(root).also { Files.createDirectories(it) }, NativeFSLockFactory.INSTANCE).use { d ->
            IndexWriter(d, IndexWriterConfig(StandardAnalyzer())).use { w ->
                val header = IndexHeader.current("user", "long", "1", StandardAnalyzer()).copy(format = "0")
                w.setLiveCommitData(header.userData().entries)
                w.commit()
            }
        }
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.HEADER)
        started.closeAll()
    }

    @Test
    fun `a repeated key in the compare scan fails and deletes nothing`(@TempDir root: Path) {
        seed(root, a, b)
        val before = names(root)
        assertThatThrownBy { start(files(root), RowStore(listOf(a, a))) }.hasMessageContaining("more than once")
        assertThat(names(root)).isEqualTo(before)
    }

    @Test
    fun `a repeated key in the build scan fails before any commit`(@TempDir root: Path) {
        assertThatThrownBy { start(files(root), RowStore(listOf(a, a))) }.hasMessageContaining("more than once")
        assertThat(names(root).none { it.startsWith("segments_") }).isTrue()
    }

    @Test
    fun `a store error in the compare keeps the previous commit`(@TempDir root: Path) {
        seed(root, a)
        assertThatThrownBy { start(files(root), RowStore(listOf(a), failOnScan = 1)) }
            .hasStackTraceContaining("injected store failure")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.REUSED)
        started.closeAll()
    }

    /** The compare scan finds a mismatch and succeeds. The build scan fails after one writer update. */
    @Test
    fun `a failed build on the mismatch path keeps the previous commit`(@TempDir root: Path) {
        seed(root, a)
        val store = RowStore(listOf(a, b))
        val encoder = FailingEncoder(store, failKey = "2", onScan = 2)
        assertThatThrownBy { start(files(root), store, entry = encoder::entryOf) }
            .hasMessageContaining("injected encoder failure")
        assertThat(store.scans).isEqualTo(2)
        assertThat(encoder.encodedInFailingScan).isEqualTo(1)
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.REUSED)
        started.closeAll()
    }

    /** A drop request skips the compare, so the build scan is the first scan. */
    @Test
    fun `a failed build on the recovery path leaves only lock and request files`(@TempDir root: Path) {
        seed(root, a)
        IndexRequests(dir(root)).write(IndexRequest.DROP)
        val store = RowStore(listOf(a, b))
        val encoder = FailingEncoder(store, failKey = "2", onScan = 1)
        assertThatThrownBy { start(files(root), store, entry = encoder::entryOf) }
            .hasMessageContaining("injected encoder failure")
        assertThat(encoder.encodedInFailingScan).isEqualTo(1)
        assertThat(names(root)).isSubsetOf("write.lock", OWNER_LOCK, "chat-drop.request", "chat-rebuild.request")
        assertThat(names(root)).contains("chat-drop.request")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_DROP)
        assertThat(names(root)).doesNotContain("chat-drop.request")
        started.closeAll()
    }

    @Test
    fun `a rebuild request builds and is cleared`(@TempDir root: Path) {
        seed(root, a)
        IndexRequests(dir(root)).write(IndexRequest.REBUILD)
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_REBUILD)
        assertThat(names(root)).doesNotContain("chat-rebuild.request")
        started.closeAll()
    }

    @Test
    fun `a drop request clears both requests`(@TempDir root: Path) {
        seed(root, a)
        IndexRequests(dir(root)).write(IndexRequest.REBUILD)
        IndexRequests(dir(root)).write(IndexRequest.DROP)
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_DROP)
        assertThat(names(root)).doesNotContain("chat-rebuild.request", "chat-drop.request")
        started.closeAll()
    }

    @Test
    fun `two node ids use two directories`(@TempDir root: Path) {
        val one = start(files(root, 1), RowStore(listOf(a)))
        val two = start(files(root, 2), RowStore(listOf(b)))
        assertThat(dir(root, 1)).isNotEqualTo(dir(root, 2))
        assertThat(one.holds("1")).isTrue()
        assertThat(two.holds("2")).isTrue()
        one.closeAll()
        two.closeAll()
    }

    @Test
    fun `one node id twice fails on the owner lock and deletes nothing`(@TempDir root: Path) {
        val one = start(files(root), RowStore(listOf(a)))
        val before = names(root)
        assertThatThrownBy { start(files(root), RowStore(listOf(a))) }.hasMessageContaining("in use by another process")
        assertThat(names(root)).isEqualTo(before)
        one.closeAll()
    }

    @Test
    /**
     * IndexWriter takes write.lock before it reads a commit. So a damaged index
     * whose write.lock another holder keeps fails at the writer, before any
     * recovery step can delete a file.
     */
    fun `write lock held elsewhere fails the start and deletes nothing`(@TempDir root: Path) {
        seed(root, a)
        Files.list(dir(root)).filter { it.fileName.toString().startsWith("segments_") }
            .forEach { RandomAccessFile(it.toFile(), "rw").use { f -> f.setLength(4) } }
        val before = names(root)
        FSDirectory.open(dir(root), NativeFSLockFactory.INSTANCE).use { other ->
            other.obtainLock(IndexWriter.WRITE_LOCK_NAME).use {
                assertThatThrownBy { start(files(root), RowStore(listOf(a))) }.hasMessageContaining("write.lock")
            }
        }
        assertThat(names(root)).isEqualTo(before)
    }

    /** Review focus 1: a root that is a regular file fails the start, and it is not damage. */
    @Test
    fun `a root that is a file fails the start`(@TempDir tmp: Path) {
        val root = Files.writeString(tmp.resolve("root-file"), "x")
        assertThatThrownBy { start(files(root), RowStore(listOf(a))) }.isInstanceOf(IOException::class.java)
        assertThat(Files.readString(root)).isEqualTo("x")
    }

    @Test
    fun `a commit failure fails the start, and the next start checks again`(@TempDir root: Path) {
        val failing = object : FileStorage(root, "long", 1) {
            override fun open(name: String): Directory = object : FilterDirectory(super.open(name)) {
                override fun rename(source: String, dest: String) {
                    throw IOException("injected rename failure")
                }
            }
        }
        assertThatThrownBy { start(failing, RowStore(listOf(a))) }.hasMessageContaining("committed state is uncertain")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.holds("1")).isTrue()
        started.closeAll()
    }

    @Test
    fun `a failure after the commit fails the start, and the request remains`(@TempDir root: Path) {
        seed(root, a)
        IndexRequests(dir(root)).write(IndexRequest.REBUILD)
        val failing = object : FileStorage(root, "long", 1) {
            override fun requests(name: String) = IndexRequests(path(name), delete = { throw IOException("injected delete failure") })
        }
        assertThatThrownBy { start(failing, RowStore(listOf(a))) }.hasStackTraceContaining("injected delete failure")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_REBUILD)
        started.closeAll()
    }

    @Test
    fun `request files survive a commit`(@TempDir root: Path) {
        seed(root, a)
        val requests = IndexRequests(dir(root))
        requests.write(IndexRequest.REBUILD)
        requests.write(IndexRequest.DROP)
        // The drop build clears both files. Write them again, then commit.
        val started = start(files(root), RowStore(listOf(a)))
        requests.write(IndexRequest.REBUILD)
        requests.write(IndexRequest.DROP)
        started.writer.updateDocument(org.apache.lucene.index.Term("_key", "9"), encode(Row("9", "x")).document)
        started.writer.commit()
        assertThat(names(root)).contains("chat-rebuild.request", "chat-drop.request")
        started.closeAll()
    }

    @Test
    fun `a lock failure that is not a held lock closes the directory`(@TempDir root: Path) {
        var closed = false
        val failing = object : FileStorage(root, "long", 1) {
            override fun open(name: String): Directory = object : FilterDirectory(super.open(name)) {
                override fun obtainLock(lockName: String): Lock = throw IOException("injected lock failure")
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        }
        assertThatThrownBy { start(failing, RowStore(listOf(a))) }.hasMessage("injected lock failure")
        assertThat(closed).isTrue()
    }

    @Test
    fun `a rollback error stops recovery before any deletion`(@TempDir root: Path) {
        seed(root, a, b)
        IndexRequests(dir(root)).write(IndexRequest.DROP)
        val before = names(root)
        assertThatThrownBy {
            start(files(root), RowStore(listOf(a)), rollback = { throw IOException("injected rollback failure") })
        }.hasMessage("injected rollback failure")
        assertThat(names(root)).isEqualTo(before)
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_DROP)
        started.closeAll()
    }

    @Test
    fun `every build syncs the directory, with no request pending`(@TempDir root: Path) {
        var syncs = 0
        val counting = object : FileStorage(root, "long", 1) {
            override fun sync(name: String) {
                syncs++
                super.sync(name)
            }
        }
        start(counting, RowStore(listOf(a))).closeAll()
        assertThat(syncs).isEqualTo(1)
    }

    @Test
    fun `a sync failure after the build fails the start, and the next start checks again`(@TempDir root: Path) {
        val failing = object : FileStorage(root, "long", 1) {
            override fun sync(name: String) {
                throw IOException("injected sync failure")
            }
        }
        assertThatThrownBy { start(failing, RowStore(listOf(a))) }.hasMessage("injected sync failure")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.holds("1")).isTrue()
        started.closeAll()
    }
}
```

**Step 3: Run the tests and see them fail**

Run: `lucene_test 'IndexStartSequenceTests'`
Expected: `exit=1`, with compile errors for `IndexStartSequence`, `Started`, and `OWNER_LOCK`.

**Step 4: Write the implementation**

`LMAIN/index/lucene/storage/IndexStartSequence.kt`:

```kotlin
package com.demo.chat.index.lucene.storage

import com.demo.chat.domain.ChatException
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.Lock
import org.apache.lucene.store.LockObtainFailedException
import org.apache.lucene.util.BytesRef
import org.apache.lucene.util.FixedBitSet
import reactor.core.publisher.Flux
import java.io.IOException

/** Held from open to close. No other process can use the index directory. */
const val OWNER_LOCK = "chat-owner.lock"

/** Recovery never deletes these files. */
private val KEPT_FILES = setOf(
    IndexWriter.WRITE_LOCK_NAME, OWNER_LOCK, IndexRequest.REBUILD.fileName, IndexRequest.DROP.fileName,
)

/** What a successful start holds. The index owns these until it closes. */
class Started(
    val directory: Directory,
    val ownerLock: Lock,
    val writer: IndexWriter,
    val manager: SearcherManager,
    val outcome: StartOutcome,
)

/**
 * The start sequence of one Lucene index. See the spec section "The start
 * sequence for one index", CHAT-ybtirmgj.
 *
 * The compare is exact only when no other process writes the store during the
 * start. CHAT-lswjobhz holds that limit.
 */
class IndexStartSequence<E> internal constructor(
    private val name: String,
    private val storage: LuceneStorage,
    private val analyzer: Analyzer,
    private val entryOf: (E) -> IndexEntry,
    private val entities: (() -> Flux<out E>)?,
    private val rollback: (IndexWriter) -> Unit,
) {
    constructor(
        name: String,
        storage: LuceneStorage,
        analyzer: Analyzer,
        entryOf: (E) -> IndexEntry,
        entities: (() -> Flux<out E>)?,
    ) : this(name, storage, analyzer, entryOf, entities, { it.rollback() })

    private val header = IndexHeader.current(name, storage.keyType, storage.nodeId, analyzer)

    private sealed interface Decision
    private data class Reuse(val compared: Long) : Decision
    private data class Build(val reason: BuildReason, val compared: Long = 0) : Decision
    private data class Recover(val reason: BuildReason) : Decision

    /** A damaged read inside the compare. The sequence recovers from it. */
    private class DamageFound(cause: Throwable?) : RuntimeException(cause)

    fun run(): Started {
        if (entities == null && storage.persistent) {
            throw ChatException(
                "${LuceneStorages.ROOT} is set, and no local store exists for Lucene index $name. " +
                    "The index files cannot be checked against a store."
            )
        }
        val scan: () -> Flux<out E> = entities ?: { Flux.empty() }
        val directory = storage.open(name)
        val ownerLock = try {
            directory.obtainLock(OWNER_LOCK)
        } catch (t: Throwable) {
            runCatching { directory.close() }.onFailure(t::addSuppressed)
            if (t is LockObtainFailedException) {
                throw ChatException(
                    "Lucene index $name is in use by another process at ${storage.describe(name)}. Nothing was deleted.", t,
                )
            }
            throw t
        }
        var writer: IndexWriter? = null
        try {
            val requests = storage.requests(name)
            requests?.removeStaleTemporary()
            val pending = requests?.pending()
            writer = openWriter(directory, OpenMode.CREATE_OR_APPEND)
            val decision = when {
                pending == IndexRequest.DROP -> Recover(BuildReason.REQUESTED_DROP)
                writer == null -> Recover(BuildReason.DAMAGE)
                pending == IndexRequest.REBUILD -> Build(BuildReason.REQUESTED_REBUILD)
                else -> inspect(directory, scan)
            }
            if (decision is Recover) writer = recover(directory, writer)
            val active = writer!!
            val outcome = when (decision) {
                is Reuse -> StartOutcome(StartKind.REUSED, null, decision.compared, 0)
                is Build -> StartOutcome(
                    StartKind.BUILT, decision.reason, decision.compared,
                    build(active, scan, requests, IndexRequests.clearedBy(pending)),
                )
                is Recover -> StartOutcome(
                    StartKind.BUILT, decision.reason, 0,
                    build(active, scan, requests, IndexRequests.clearedBy(pending)),
                )
            }
            // Every build syncs the directory inside build(), before this manager exists.
            return Started(directory, ownerLock, active, SearcherManager(directory, null), outcome)
        } catch (t: Throwable) {
            writer?.let { rollbackQuietly(it, t) }
            runCatching { directory.close() }.onFailure(t::addSuppressed)
            runCatching { ownerLock.close() }.onFailure(t::addSuppressed)
            throw t
        }
    }

    private fun config(mode: OpenMode) = IndexWriterConfig(analyzer).setOpenMode(mode).setCommitOnClose(false)

    /** Returns null when the files are damaged. Any other error fails the start. */
    private fun openWriter(directory: Directory, mode: OpenMode): IndexWriter? = try {
        IndexWriter(directory, config(mode))
    } catch (e: IOException) {
        if (Damage.isDamage(e)) null else throw e
    }

    private fun inspect(directory: Directory, scan: () -> Flux<out E>): Decision {
        val reader = try {
            DirectoryReader.open(directory)
        } catch (e: IndexNotFoundException) {
            return Build(BuildReason.NO_COMMIT)
        } catch (e: IOException) {
            if (Damage.isDamage(e)) return Recover(BuildReason.DAMAGE) else throw e
        }
        reader.use {
            if (IndexHeader.read(reader.indexCommit.userData) != header) return Build(BuildReason.HEADER)
            try {
                reader.leaves().forEach { leaf -> leaf.reader().checkIntegrity() }
            } catch (e: IOException) {
                if (Damage.isDamage(e)) return Recover(BuildReason.DAMAGE) else throw e
            }
            return try {
                compare(reader, scan)
            } catch (e: DamageFound) {
                Recover(BuildReason.DAMAGE)
            }
        }
    }

    /**
     * Each store entity must match exactly one live document with equal bytes.
     * A lookup that reaches a marked document proves a repeated store key.
     */
    private fun compare(reader: DirectoryReader, scan: () -> Flux<out E>): Decision {
        val searcher = IndexSearcher(reader)
        val seen = FixedBitSet(maxOf(reader.maxDoc(), 1))
        var compared = 0L
        scan().toStream().use { stream ->
            for (entity in stream.iterator()) {
                val entry = entryOf(entity)
                compared++
                val hits = lucene { searcher.search(TermQuery(Term(IndexFields.EXACT_KEY, entry.keyText)), 2).scoreDocs }
                if (hits.size != 1) return Build(BuildReason.MISMATCH, compared)
                val doc = hits[0].doc
                if (seen.get(doc)) {
                    throw ChatException(
                        "The store emitted key ${entry.keyText} more than once. Lucene index $name cannot be checked against it."
                    )
                }
                seen.set(doc)
                val stored = lucene { searcher.doc(doc, setOf(IndexFields.ENTRY)).getBinaryValue(IndexFields.ENTRY) }
                    ?: throw DamageFound(null)
                if (!stored.bytesEquals(BytesRef(entry.bytes))) return Build(BuildReason.MISMATCH, compared)
            }
        }
        return if (reader.numDocs().toLong() == compared) Reuse(compared) else Build(BuildReason.MISMATCH, compared)
    }

    private inline fun <R> lucene(read: () -> R): R = try {
        read()
    } catch (e: IOException) {
        if (Damage.isDamage(e)) throw DamageFound(e) else throw e
    }

    /**
     * Deletes every index file under write.lock. Neither lock nor any request
     * file is deleted. A rollback error stops recovery before any deletion,
     * because a writer that did not roll back can still hold write.lock or
     * pending files.
     */
    private fun recover(directory: Directory, writer: IndexWriter?): IndexWriter {
        if (writer != null && writer.isOpen) rollback(writer)
        val writeLock = try {
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME)
        } catch (e: LockObtainFailedException) {
            throw ChatException(
                "Lucene index $name needs recovery, and another holder keeps ${IndexWriter.WRITE_LOCK_NAME}. Nothing was deleted.", e,
            )
        }
        writeLock.use {
            directory.listAll().filterNot { it in KEPT_FILES }.forEach(directory::deleteFile)
        }
        return IndexWriter(directory, config(OpenMode.CREATE))
    }

    /** Checks every condition before the one commit. Returns the documents written. */
    private fun build(
        writer: IndexWriter,
        scan: () -> Flux<out E>,
        requests: IndexRequests?,
        cleared: List<IndexRequest>,
    ): Long {
        var scanned = 0L
        try {
            writer.deleteAll()
            scan().toStream().use { stream ->
                stream.forEach { entity ->
                    val entry = entryOf(entity)
                    writer.updateDocument(Term(IndexFields.EXACT_KEY, entry.keyText), entry.document)
                    scanned++
                }
            }
            DirectoryReader.open(writer, true, false).use { pending ->
                if (pending.numDocs().toLong() != scanned) {
                    throw ChatException(
                        "The store emitted at least one key more than once. Lucene index $name was not committed."
                    )
                }
            }
        } catch (t: Throwable) {
            rollbackQuietly(writer, t)
            throw t
        }
        writer.setLiveCommitData(header.userData().entries)
        try {
            writer.commit()
        } catch (t: Throwable) {
            rollbackQuietly(writer, t)
            throw ChatException(
                "The build commit of Lucene index $name failed. The committed state is uncertain. The next start checks it.", t,
            )
        }
        requests?.clear(cleared)
        storage.sync(name)
        return scanned
    }

    private fun rollbackQuietly(writer: IndexWriter, primary: Throwable?) {
        try {
            if (writer.isOpen) writer.rollback()
        } catch (e: Throwable) {
            primary?.addSuppressed(e)
        }
    }
}
```

Two notes for the implementer:

- `ChatException` is a checked Java exception. `run()` throws it out of a Reactor `Mono.fromRunnable` in Task 7. `RootKeyStartup` catches `RuntimeException` only, but `block()` wraps a checked exception in a `RuntimeException`, so the start still fails with the cause attached.
- A store error from `toStream()` reaches `run()` as a `RuntimeException`. It is not `DamageFound`, so the start fails, as the spec requires.

**Step 5: Run the tests and see them pass**

Run: `lucene_test 'IndexStartSequenceTests'`
Expected: `exit=0`, and `Tests run: 29, Failures: 0, Errors: 0`.

If `damaged files recover and build` reports `MISMATCH` or `REUSED` in place of `DAMAGE`, the overwrite hit bytes that no check reads. Do not weaken the assertion. Print `Files.list(dir(root))` and pick a file that `checkIntegrity` verifies, for example the `.cfs` file. Record the measured file name in the test comment.

**Step 6: Run the four mutations of this task**

Mutation 1 bypasses the whole compare decision. In `inspect`, replace `compare(reader, scan)` with `Reuse(reader.numDocs().toLong())`.
Run: `lucene_test 'IndexStartSequenceTests'`
Expected: `exit=1`. `different content builds`, `a changed field builds`, and `an empty store empties the index` fail.

Mutation 2: in `compare`, delete the `if (seen.get(doc)) { ... }` block.
Expected: `a repeated key in the compare scan fails and deletes nothing` fails.

Mutation 3: in `build`, delete the `DirectoryReader.open(writer, true, false).use { ... }` block.
Expected: `a repeated key in the build scan fails before any commit` fails.

Mutation 4: in `recover`, replace `filterNot { it in KEPT_FILES }` with `filterNot { it == IndexWriter.WRITE_LOCK_NAME || it == OWNER_LOCK }`.
Expected: `a failed build on the recovery path leaves only lock and request files` fails.

Run each mutation alone. After each one, restore the staged file by its absolute path, and prove the restore:

```bash
git add \
  /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexStartSequence.kt \
  /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/StartFixtures.kt \
  /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/IndexStartSequenceTests.kt   # once, before the first mutation
git checkout -- /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexStartSequence.kt
git diff --stat
```

Expected: `git diff --stat` prints nothing.

**Step 7: Commit**

```bash
git add \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexStartSequence.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/StartFixtures.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/storage/IndexStartSequenceTests.kt
git commit -m "Add the Lucene index start sequence (CHAT-ybtirmgj)

A start compares the canonical bytes of each stored entity with the
committed index. Equal content reuses the files. A missing, damaged,
or different index builds with one commit after every check. Recovery
deletes index files only under write.lock, and it keeps both locks
and both request files.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The index lifecycle and runtime operations

**Issue:** `CHAT-nungovfh`. Start with `fp issue update --status in-progress CHAT-nungovfh`. End with a comment and `fp issue update --status done CHAT-nungovfh`.

**Files:**
- Create: `LMAIN/index/lucene/impl/IndexState.kt`, `LTEST/memory/LuceneTestIndexes.kt`, `LTEST/index/lucene/LuceneIndexLifecycleTests.kt`
- Modify: `LMAIN/index/lucene/impl/LuceneIndex.kt` (whole file), `MembershipLuceneIndex.kt:18-27`, `KeyValueLuceneIndex.kt:24-41`
- Modify tests: `AuthMetaIndexTests.kt`, `KeyValueIndexTests.kt`, `MessageIndexTests.kt`, `MessageTopicIndexTests.kt`, `MessageTopicQueryTests.kt`, `TopicMembershipIndexTests.kt`, `UserIndexTests.kt`, `LuceneIndexBeansRootTests.kt`

**Interfaces:**
- Consumes: `IndexStartSequence`, `Started`, `LuceneStorage`, `MemoryStorage`, `IndexEntry`, `StartOutcome` from Tasks 2 to 5.
- Produces on `LuceneIndex<T, E>`:

```kotlin
fun open(name: String, storage: LuceneStorage, entities: (() -> Flux<out E>)?)
fun openInMemory(name: String = javaClass.simpleName)
fun close()
fun state(): IndexState
fun startOutcome(): StartOutcome?
fun liveDocuments(): Int?
internal var beforeCommit: () -> Unit
protected fun <R> withSearcher(read: (IndexSearcher) -> R): R
```

- Produces, test only: `fun <I : LuceneIndex<*, *>> I.openedInMemory(): I`

**Step 1: Write the test helper and the failing lifecycle tests**

`LTEST/memory/LuceneTestIndexes.kt`:

```kotlin
package com.demo.chat.test.memory

import com.demo.chat.index.lucene.impl.LuceneIndex

/** Opens an index in memory mode, as the start load does in production. */
fun <I : LuceneIndex<*, *>> I.openedInMemory(): I = apply { openInMemory() }
```

`LTEST/index/lucene/LuceneIndexLifecycleTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.impl.IndexState
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import com.demo.chat.index.lucene.storage.StartKind
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FilterDirectory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Flux
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The lifecycle of one index. `beforeCommit` is internal, and a test source set
 * of the same Maven module can set it.
 */
class LuceneIndexLifecycleTests {

    data class Doc(val id: Long, val name: String)

    private companion object {
        /** A fixed root. These tests read no root. */
        const val ROOT = -9L
    }

    private fun index(encoder: (Doc) -> List<Pair<String, String>> = { listOf("name" to it.name) }) =
        LuceneIndex<Long, Doc>(encoder, { s -> Key.of(s.toLong(), ROOT) }, { d -> Key.of(d.id, ROOT) })

    private fun names(index: LuceneIndex<Long, Doc>, q: String) =
        index.findBy(IndexSearchRequest("name", q, 10)).map { it.id }.collectList().block()!!

    @Test
    fun `an operation before open fails`() {
        val index = index()
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasMessageContaining("is not open")
        assertThatThrownBy { index.findBy(IndexSearchRequest("name", "a", 1)).blockFirst() }.hasMessageContaining("is not open")
    }

    @Test
    fun `an operation after close fails, and a second close does not throw`() {
        val index = index().apply { openInMemory("doc") }
        index.close()
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasMessageContaining("Lucene index doc is closed")
        assertThatCode { index.close() }.doesNotThrowAnyException()
    }

    @Test
    fun `close is safe before open`() {
        assertThatCode { index().close() }.doesNotThrowAnyException()
    }

    @Test
    fun `two adds of one key give one hit with no old fields`() {
        val index = index().apply { openInMemory() }
        index.add(Doc(1, "old")).block()
        index.add(Doc(1, "new")).block()
        assertThat(names(index, "new")).containsExactly(1L)
        assertThat(names(index, "old")).isEmpty()
        assertThat(index.liveDocuments()).isEqualTo(1)
    }

    /** Pause after the writer change and before the commit. A query answers from the previous commit. */
    @Test
    fun `a query during a paused mutation answers from the previous commit`() {
        val index = index().apply { openInMemory() }
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(3)
        try {
            index.add(Doc(1, "before")).block()
            index.beforeCommit = { reached.countDown(); release.await(5, TimeUnit.SECONDS) }
            val write = pool.submit { index.add(Doc(1, "after")).block() }
            assertThat(reached.await(5, TimeUnit.SECONDS)).isTrue()

            // Each query runs on its own thread under a deadline. A query that waits for the mutation fails here.
            val before = pool.submit<List<Long>> { names(index, "before") }
            val after = pool.submit<List<Long>> { names(index, "after") }
            assertThat(before.get(5, TimeUnit.SECONDS)).containsExactly(1L)
            assertThat(after.get(5, TimeUnit.SECONDS)).isEmpty()

            release.countDown()
            write.get(5, TimeUnit.SECONDS)
            assertThat(names(index, "after")).containsExactly(1L)
        } finally {
            release.countDown()
            index.beforeCommit = {}
            pool.shutdownNow()
            index.close()
        }
    }

    @Test
    fun `a commit failure moves the index to FAILED and commits nothing`(@TempDir root: Path) {
        var failRename = false
        val storage = object : FileStorage(root, "long", 1) {
            override fun open(name: String): Directory = object : FilterDirectory(super.open(name)) {
                override fun rename(source: String, dest: String) {
                    if (failRename) throw IOException("injected rename failure")
                    super.rename(source, dest)
                }
            }
        }
        val index = index().apply { open("doc", storage) { Flux.empty() } }
        failRename = true
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasStackTraceContaining("injected rename failure")
        assertThat(index.state()).isEqualTo(IndexState.FAILED)
        assertThatThrownBy { index.add(Doc(2, "b")).block() }.hasMessageContaining("Lucene index doc failed")
        index.close()

        val reopened = index().apply { open("doc", FileStorage(root, "long", 1)) { Flux.empty() } }
        assertThat(reopened.startOutcome()!!.kind).isEqualTo(StartKind.REUSED)
        assertThat(reopened.liveDocuments()).isEqualTo(0)
        reopened.close()
    }

    @Test
    fun `an encoder failure keeps the index OPEN`() {
        val index = index { throw IllegalArgumentException("injected encoder failure") }.apply { openInMemory() }
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasMessageContaining("injected encoder failure")
        assertThat(index.state()).isEqualTo(IndexState.OPEN)
    }

    /** Review focus 5. */
    @Test
    fun `a query with bad syntax fails and the index stays OPEN`() {
        val index = index().apply { openInMemory() }
        assertThatThrownBy { index.findBy(IndexSearchRequest("name", "(unclosed", 10)).blockFirst() }
        assertThat(index.state()).isEqualTo(IndexState.OPEN)
    }

    @Test
    fun `a reopen after close reuses the files`(@TempDir root: Path) {
        val docs = listOf(Doc(1, "a"), Doc(2, "b"))
        val first = index().apply { open("doc", FileStorage(root, "long", 1)) { Flux.fromIterable(docs) } }
        first.close()
        val second = index().apply { open("doc", FileStorage(root, "long", 1)) { Flux.fromIterable(docs) } }
        assertThat(second.startOutcome()!!.kind).isEqualTo(StartKind.REUSED)
        assertThat(second.startOutcome()!!.documentsWritten).isEqualTo(0)
        assertThat(names(second, "b")).containsExactly(2L)
        second.close()
    }

    @Test
    fun `a query during the start fails`() {
        val index = index()
        val inStore = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val opening = pool.submit {
                index.open("doc", MemoryStorage()) {
                    Flux.defer { inStore.countDown(); release.await(5, TimeUnit.SECONDS); Flux.just(Doc(1, "a")) }
                }
            }
            assertThat(inStore.await(5, TimeUnit.SECONDS)).isTrue()
            val query = pool.submit<Throwable?> { runCatching { names(index, "a") }.exceptionOrNull() }
            assertThat(query.get(5, TimeUnit.SECONDS)).hasMessageContaining("is not open")
            release.countDown()
            opening.get(5, TimeUnit.SECONDS)
            assertThat(names(index, "a")).containsExactly(1L)
        } finally {
            // Release first, so the open ends and close can take the lock.
            release.countDown()
            pool.shutdownNow()
            index.close()
        }
    }

    @Test
    fun `an encoder failure on an index that is not open reports the state`() {
        val index = index { throw IllegalArgumentException("injected encoder failure") }
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasMessageContaining("is not open")
    }
}
```

`open` takes the mutation lock, and a query does not. So a query during `open` sees `NEW` and fails, as the spec requires.

**Step 2: Run the lifecycle tests and see them fail**

Run: `lucene_test 'LuceneIndexLifecycleTests'`
Expected: `exit=1`, with compile errors for `open`, `openInMemory`, `state`, `IndexState`, and `beforeCommit`.

**Step 3: Write `IndexState.kt` and replace `LuceneIndex.kt`**

`LMAIN/index/lucene/impl/IndexState.kt`:

```kotlin
package com.demo.chat.index.lucene.impl

/** The lifecycle of one Lucene index. No operation opens an index implicitly. */
enum class IndexState { NEW, OPEN, FAILED, CLOSED }
```

`LMAIN/index/lucene/impl/LuceneIndex.kt`, the whole file:

```kotlin
package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.storage.IndexEntry
import com.demo.chat.index.lucene.storage.IndexFields
import com.demo.chat.index.lucene.storage.IndexStartSequence
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import com.demo.chat.index.lucene.storage.StartOutcome
import com.demo.chat.index.lucene.storage.Started
import com.demo.chat.service.core.IndexService
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function
import kotlin.concurrent.withLock

/**
 * One Lucene index. See CHAT-ybtirmgj.
 *
 * The index opens explicitly. The start load calls [open], and a test calls
 * [openInMemory]. Each runtime mutation changes the writer, commits, and
 * refreshes the searcher under one lock. A query only acquires and releases a
 * searcher, so it never waits for a mutation. The searcher manager reads
 * committed data only, so a query never sees an uncommitted change.
 */
open class LuceneIndex<T, E>(
    private val entityEncoder: Function<E, List<Pair<String, String>>>,
    private val keyEncoder: Function<String, Key<T>>,
    private val keyReceiver: Function<E, Key<T>>,
) : IndexService<T, E, IndexSearchRequest> {

    val analyzer = StandardAnalyzer()

    private val logger = LoggerFactory.getLogger(javaClass)
    private val mutation = ReentrantLock()

    @Volatile private var state: IndexState = IndexState.NEW
    @Volatile private var started: Started? = null
    @Volatile private var failure: Throwable? = null
    @Volatile private var name: String = javaClass.simpleName

    /** A test seam. It runs after the writer change and before the commit. */
    internal var beforeCommit: () -> Unit = {}

    /** Runs the start sequence. A failure leaves the index FAILED and releases what the sequence obtained. */
    fun open(name: String, storage: LuceneStorage, entities: (() -> Flux<out E>)?) {
        mutation.withLock {
            check(state == IndexState.NEW) { "Lucene index $name was already opened. Its state is $state." }
            this.name = name
            try {
                val result = IndexStartSequence(name, storage, analyzer, ::entryOf, entities).run()
                started = result
                state = IndexState.OPEN
                logger.info(result.outcome.logLine(name))
            } catch (t: Throwable) {
                failure = t
                state = IndexState.FAILED
                throw t
            }
        }
    }

    fun openInMemory(name: String = javaClass.simpleName) = open(name, MemoryStorage(), null)

    fun state(): IndexState = state

    fun startOutcome(): StartOutcome? = started?.outcome

    fun liveDocuments(): Int? = if (state == IndexState.OPEN) withSearcher { it.indexReader.numDocs() } else null

    /** The fields are read and checked before any writer change, so an encoder error keeps the index OPEN. */
    internal fun entryOf(entity: E): IndexEntry =
        IndexEntry.of(keyReceiver.apply(entity).id.toString(), entityEncoder.apply(entity))

    /**
     * The state check runs before the encoder, so an encoder error cannot hide
     * a NEW, FAILED or CLOSED index. The mutation checks the state again under
     * the lock.
     */
    override fun add(entity: E): Mono<Void> =
        Mono.fromCallable { requireOpen(); entryOf(entity) }
            .flatMap { entry ->
                mutate { writer -> writer.updateDocument(Term(IndexFields.EXACT_KEY, entry.keyText), entry.document) }
            }

    /** Removes the document of one key, and only that document. The exact field is not analyzed. */
    override fun rem(key: Key<T>): Mono<Void> =
        mutate { writer -> writer.deleteDocuments(Term(IndexFields.EXACT_KEY, key.id.toString())) }

    override fun findBy(query: IndexSearchRequest): Flux<out Key<T>> = Flux.defer {
        val keys = withSearcher { searcher ->
            searcher.search(QueryParser(query.first, analyzer).parse(query.second), query.config).scoreDocs
                .map { searcher.doc(it.doc).get(IndexFields.STORED_KEY) }
        }
        Flux.fromIterable(keys.map(keyEncoder::apply))
    }

    override fun findUnique(query: IndexSearchRequest): Mono<out Key<T>> = findBy(query).singleOrEmpty()

    protected fun <R> withSearcher(read: (IndexSearcher) -> R): R {
        val manager = requireOpen().manager
        val searcher = manager.acquire()
        try {
            return read(searcher)
        } finally {
            manager.release(searcher)
        }
    }

    /**
     * Idempotent, and safe in every state. It waits for a running mutation. It
     * never commits, because the writer config sets commit-on-close to false.
     * The owner lock is released last.
     */
    fun close() {
        mutation.withLock {
            if (state == IndexState.CLOSED) return
            val held = started
            started = null
            state = IndexState.CLOSED
            val errors = mutableListOf<Throwable>()
            val step = { block: () -> Unit -> try { block() } catch (e: Throwable) { errors += e } }
            if (held != null) {
                step { held.manager.close() }
                step { held.writer.close() }
                step { held.directory.close() }
            }
            step { analyzer.close() }
            if (held != null) step { held.ownerLock.close() }
            errors.firstOrNull()?.let { first ->
                errors.drop(1).forEach(first::addSuppressed)
                throw first
            }
        }
    }

    private fun mutate(change: (IndexWriter) -> Unit): Mono<Void> = Mono.fromRunnable {
        mutation.withLock {
            val held = requireOpen()
            try {
                change(held.writer)
                beforeCommit()
                held.writer.commit()
                held.manager.maybeRefreshBlocking()
            } catch (t: Throwable) {
                fail(held, t)
                throw t
            }
        }
    }

    /** Best effort, and nothing commits. The owner lock stays held until close. */
    private fun fail(held: Started, cause: Throwable) {
        failure = cause
        state = IndexState.FAILED
        try { held.writer.rollback() } catch (e: Throwable) { cause.addSuppressed(e) }
        try { held.manager.close() } catch (e: Throwable) { cause.addSuppressed(e) }
    }

    private fun requireOpen(): Started = when (state) {
        IndexState.OPEN -> started ?: throw IllegalStateException("Lucene index $name is closed")
        IndexState.NEW -> throw IllegalStateException("Lucene index $name is not open")
        IndexState.FAILED -> throw IllegalStateException("Lucene index $name failed: ${failure?.message}", failure)
        IndexState.CLOSED -> throw IllegalStateException("Lucene index $name is closed")
    }
}
```

`EXACT_KEY` moved from the `LuceneIndex` companion to `IndexFields`. Find every user of the old constant with the language server before you build:

`mcp__treesitter-mcp__find_usages` on `EXACT_KEY` in `chat-index-lucene`. Change each user to `IndexFields.EXACT_KEY`.

**Step 4: Update the two subclasses**

`MembershipLuceneIndex.kt`, replace the `size` body:

```kotlin
    override fun size(query: IndexSearchRequest): Mono<Long> = Mono.fromCallable {
        withSearcher { searcher ->
            searcher.search(QueryParser(query.first, analyzer).parse(query.second), query.config).totalHits.value
        }
    }
```

Remove the now unused imports `DirectoryReader` and `IndexSearcher`.

`KeyValueLuceneIndex.kt`, delete the `add` override and its comment. Replace the class KDoc with:

```kotlin
/**
 * A key-value entry is mutable, so this index replaces the entry of a key.
 *
 * The base index replaces by key with one atomic writer call, and it reads
 * and checks the fields before that call. An unregistered value type and a
 * field that uses a reserved name both fail without changing the index. A job
 * that reached SUCCEEDED therefore never stays searchable as RUNNING.
 */
```

Remove the `entryEncoder` property and the `Mono` import if nothing else uses them. Keep `entryEncoder` as the constructor argument that passes to the base class.

If the language server finds other users of the old `LuceneIndex.EXACT_KEY` constant, change them and add each changed file to the Step 7 `git add` list.

**Step 5: Open every test index, and give the fixed-key tests distinct keys**

For each of these constructions, append `.openedInMemory()` and import `com.demo.chat.test.memory.openedInMemory` where the file sits in another package:

| File | Constructions |
|---|---|
| `AuthMetaIndexTests.kt` | 1 |
| `KeyValueIndexTests.kt` | 5 |
| `MessageIndexTests.kt` | 1 |
| `MessageTopicIndexTests.kt` | 1 |
| `MessageTopicQueryTests.kt` | 1 |
| `TopicMembershipIndexTests.kt` | 1 |
| `UserIndexTests.kt` | 1 |

In `LuceneIndexBeansRootTests.kt`, open each index after the bean call, for example:

```kotlin
val index = beans.userIndex().also { (it as LuceneIndex<*, *>).openInMemory() }
```

Four tests reuse one key for every supplied value. Add replace-by-key semantics, and their two-hit test would see one hit. Give each supply a fresh key from a counter. Add at the bottom of each file:

```kotlin
private val NEXT_ID = java.util.concurrent.atomic.AtomicLong(1000L)
```

and change the supplies:

| File | Old supply | New supply |
|---|---|---|
| `MessageIndexTests.kt:22` | `TestKeys.message(1234L, 1L, 2L)` | `TestKeys.message(NEXT_ID.incrementAndGet(), 1L, 2L)` |
| `MessageTopicIndexTests.kt` | `TestKeys.key(1234L)` | `TestKeys.key(NEXT_ID.incrementAndGet())` |
| `TopicMembershipIndexTests.kt` | `TopicMembership.create(123456L, 1234L, 12345L)` | `TopicMembership.create(NEXT_ID.incrementAndGet(), 1234L, 12345L)` |
| `AuthMetaIndexTests.kt` | `TestKeys.key(1234L)` as the first argument | `TestKeys.key(NEXT_ID.incrementAndGet())` |

The `keyExtract` functions read the key from the supplied value, so they still match.

**Step 6: Run the whole Lucene module and see it pass**

Run the module tests with no filter:

```bash
mvn -o -B -pl chat-core,chat-index-lucene test > "$LOG" 2>&1; echo "exit=$?"; grep -E "Tests run:.*Fail|ERROR\]" "$LOG" | tail -12
```

Expected: `exit=0`. Every Lucene test class passes. `LuceneIndexLifecycleTests` reports `Tests run: 11`.

**Step 7: Commit**

```bash
git add \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/impl/IndexState.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/impl/LuceneIndex.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/impl/MembershipLuceneIndex.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/impl/KeyValueLuceneIndex.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/LuceneTestIndexes.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/LuceneIndexLifecycleTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/AuthMetaIndexTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/KeyValueIndexTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/MessageIndexTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/MessageTopicIndexTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/MessageTopicQueryTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/TopicMembershipIndexTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/UserIndexTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/LuceneIndexBeansRootTests.kt
git commit -m "Give the Lucene index an explicit lifecycle (CHAT-ybtirmgj)

An index opens through the start sequence and closes once. A mutation
commits and refreshes under one lock, and a query reads committed data
through a SearcherManager. Add replaces by key. A writer or commit
error moves the index to FAILED and commits nothing. The readers no
longer leak.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The load, the beans, and the registry

**Issue:** `CHAT-pfvbyyhx`. Start with `fp issue update --status in-progress CHAT-pfvbyyhx`. End with a comment and `fp issue update --status done CHAT-pfvbyyhx`.

**Files:**
- Create: `LMAIN/index/lucene/LuceneIndexLoad.kt`, `LMAIN/index/lucene/LuceneIndexRegistry.kt`
- Modify: `LMAIN/config/LuceneIndexBeans.kt`
- Test: `LTEST/index/lucene/LuceneIndexRegistryTests.kt`, `LTEST/index/lucene/LuceneIndexBeansStorageTests.kt`, `LTEST/index/lucene/LuceneIndexLoadTests.kt`

**Interfaces:**
- Consumes: `LuceneIndex.open`, `LuceneStorages.of`, `IndexRequests`, `IndexFileAdmin`.
- Produces: `class LuceneIndexLoad<T, E : Any>(index: LuceneIndex<T, E>, name: String, storage: LuceneStorage, store: PersistenceStore<T, E>?) : StartupIndexLoad`
- Produces: `class LuceneIndexRegistry(storage: LuceneStorage, indexes: Map<String, LuceneIndex<*, *>>) : IndexFileAdmin`
- Produces: `object LuceneIndexNames { USER, MESSAGE, TOPIC, MEMBERSHIP, AUTH, KEY_VALUE }` in `LuceneIndexRegistry.kt`
- Produces: beans `luceneStorage(): LuceneStorage` and `luceneIndexRegistry(): LuceneIndexRegistry` on `LuceneIndexBeans`

**Step 1: Write the failing tests**

`LTEST/index/lucene/LuceneIndexRegistryTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene

import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.LuceneIndexRegistry
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Path

class LuceneIndexRegistryTests {

    private fun index() = LuceneIndex<Long, String>({ listOf("v" to it) }, { Key.of(it.toLong(), -9L) }, { Key.of(it.length.toLong(), -9L) })

    @Test
    fun `memory mode refuses both commands`() {
        val registry = LuceneIndexRegistry(MemoryStorage(), mapOf("user" to index().apply { openInMemory("user") }))
        val rebuild = registry.requestRebuild("user")
        val drop = registry.requestDrop("user")
        assertThat(rebuild.accepted).isFalse()
        assertThat(drop.accepted).isFalse()
        assertThat(rebuild.reason).contains("in memory")
    }

    @Test
    fun `an unknown name lists the indexes`() {
        val registry = LuceneIndexRegistry(MemoryStorage(), mapOf("user" to index(), "topic" to index()))
        val result = registry.requestRebuild("nope")
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("user, topic")
    }

    @Test
    fun `files mode writes the request and reports it`(@TempDir root: Path) {
        val storage = FileStorage(root, "long", 1)
        val user = index().apply { open("user", storage) { Flux.just("abc") } }
        val registry = LuceneIndexRegistry(storage, mapOf("user" to user))
        assertThat(registry.requestDrop("user").accepted).isTrue()
        assertThat(Files.exists(root.resolve("long/1/user/chat-drop.request"))).isTrue()
        val report = registry.reports().single()
        assertThat(report.mode).isEqualTo("files")
        assertThat(report.state).isEqualTo("OPEN")
        assertThat(report.outcome).isEqualTo("BUILT")
        assertThat(report.reason).isEqualTo("NO_COMMIT")
        assertThat(report.liveDocuments).isEqualTo(1)
        assertThat(report.pendingRequest).isEqualTo("DROP")
        user.close()
    }
}
```

`LTEST/index/lucene/LuceneIndexBeansStorageTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene

import com.demo.chat.config.LuceneIndexBeans
import com.demo.chat.domain.LongUtil
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import com.demo.chat.test.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory
import java.nio.file.Path

/**
 * These tests call luceneStorage() alone. An instance built without Spring has
 * no bean proxy, so two calls of one bean method give two objects. A test that
 * needs one index across two bean methods must use a Spring context.
 */
class LuceneIndexBeansStorageTests {

    private val entries: ObjectProvider<KeyValueIndexFieldsEntry> =
        StaticListableBeanFactory().getBeanProvider(KeyValueIndexFieldsEntry::class.java)

    private fun beans(root: String?) = LuceneIndexBeans(LongUtil(), FakeKeyServices.longRoots(), entries, root, "long", 7)

    @Test
    fun `no root gives memory storage`() {
        assertThat(beans(null).luceneStorage()).isInstanceOf(MemoryStorage::class.java)
    }

    @Test
    fun `a root gives file storage`(@TempDir root: Path) {
        assertThat(beans(root.toString()).luceneStorage()).isInstanceOf(FileStorage::class.java)
    }

    @Test
    fun `a blank root fails`() {
        assertThatThrownBy { beans(" ").luceneStorage() }.hasMessageContaining("app.index.lucene.root")
    }
}
```

`LTEST/index/lucene/LuceneIndexLoadTests.kt`:

```kotlin
package com.demo.chat.test.index.lucene

import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.LuceneIndexLoad
import com.demo.chat.index.lucene.impl.IndexState
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Plan decision 1: the no-store behaviour of each mode. */
class LuceneIndexLoadTests {

    private fun index() = LuceneIndex<Long, String>({ listOf("v" to it) }, { Key.of(it.toLong(), -9L) }, { Key.of(it.length.toLong(), -9L) })

    @Test
    fun `memory mode with no store opens empty`() {
        val index = index()
        LuceneIndexLoad(index, "user", MemoryStorage(), null).load().block()
        assertThat(index.state()).isEqualTo(IndexState.OPEN)
        assertThat(index.liveDocuments()).isEqualTo(0)
        index.close()
    }

    @Test
    fun `files mode with no store fails and touches no file`(@TempDir root: Path) {
        val index = index()
        assertThatThrownBy { LuceneIndexLoad(index, "user", FileStorage(root, "long", 1), null).load().block() }
            .hasStackTraceContaining("no local store exists")
        assertThat(index.state()).isEqualTo(IndexState.FAILED)
        assertThat(Files.exists(root.resolve("long"))).isFalse()
    }
}
```

Before Step 2, confirm with the language server that `FakeKeyServices` and `LongUtil` live at the imported packages. `MessageIndexTests.kt` imports both, so copy its import lines if they differ.

**Step 2: Run the tests and see them fail**

Run: `lucene_test 'LuceneIndexRegistryTests,LuceneIndexBeansStorageTests,LuceneIndexLoadTests'`
Expected: `exit=1`, with compile errors for `LuceneIndexRegistry`, `luceneStorage`, and the new constructor.

**Step 3: Write the load and the registry**

`LMAIN/index/lucene/LuceneIndexLoad.kt`:

```kotlin
package com.demo.chat.index.lucene

import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.StartupIndexLoad
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

/**
 * Opens one Lucene index at start, against its store. See CHAT-ybtirmgj.
 *
 * With no store, memory mode opens the index empty, and files mode fails the
 * start, because no store can check the files.
 */
class LuceneIndexLoad<T, E : Any>(
    private val index: LuceneIndex<T, E>,
    private val name: String,
    private val storage: LuceneStorage,
    private val store: PersistenceStore<T, E>?,
) : StartupIndexLoad {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun load(): Mono<Void> = Mono.fromRunnable {
        if (store == null) {
            logger.warn("lucene index $name: no local store exists. Memory mode opens the index empty.")
        }
        index.open(name, storage, store?.let { s -> { s.all() } })
    }
}
```

`LMAIN/index/lucene/LuceneIndexRegistry.kt`:

```kotlin
package com.demo.chat.index.lucene

import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.IndexRequest
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.service.core.IndexFileAdmin
import com.demo.chat.service.core.IndexFileReport
import com.demo.chat.service.core.IndexRequestResult

object LuceneIndexNames {
    const val USER = "user"
    const val MESSAGE = "message"
    const val TOPIC = "topic"
    const val MEMBERSHIP = "membership"
    const val AUTH = "auth"
    const val KEY_VALUE = "keyvalue"
}

/** The operator view of the six Lucene indexes. A request takes effect at the next start. */
class LuceneIndexRegistry(
    private val storage: LuceneStorage,
    private val indexes: Map<String, LuceneIndex<*, *>>,
) : IndexFileAdmin {

    override fun reports(): List<IndexFileReport> = indexes.map { (name, index) ->
        val outcome = index.startOutcome()
        IndexFileReport(
            name = name,
            mode = storage.mode,
            path = storage.path(name)?.toString(),
            state = index.state().name,
            liveDocuments = index.liveDocuments(),
            outcome = outcome?.kind?.name,
            reason = outcome?.reason?.name,
            entitiesCompared = outcome?.entitiesCompared,
            documentsWritten = outcome?.documentsWritten,
            pendingRequest = storage.requests(name)?.pending()?.name,
        )
    }

    override fun requestRebuild(name: String) = request(name, IndexRequest.REBUILD)

    override fun requestDrop(name: String) = request(name, IndexRequest.DROP)

    private fun request(name: String, kind: IndexRequest): IndexRequestResult {
        if (name !in indexes) {
            return IndexRequestResult(
                false, "No Lucene index is named '$name'. The indexes are: ${indexes.keys.joinToString(", ")}.", null,
            )
        }
        val requests = storage.requests(name) ?: return IndexRequestResult(
            false, "The Lucene indexes are in memory. Every start already builds them, so no request is needed.", null,
        )
        return requests.write(kind)
    }
}
```

**Step 4: Rewire `LuceneIndexBeans`**

Replace the constructor:

```kotlin
open class LuceneIndexBeans<T>(
    private val typeUtil: TypeUtil<T>,
    private val rootKeys: RootKeys<T>,
    private val keyValueFieldEntries: ObjectProvider<KeyValueIndexFieldsEntry>,
    @Value("\${app.index.lucene.root:#{null}}") private val root: String? = null,
    @Value("\${app.key.type:#{null}}") private val keyType: String? = null,
    @Value("\${app.nodeid:#{null}}") private val nodeId: Int? = null,
) : IndexServiceBeans<T, String, IndexSearchRequest> {

    private val logger = LoggerFactory.getLogger(javaClass)
```

Add `@Bean(destroyMethod = "close")` to `userIndex`, `messageIndex`, `topicIndex`, `membershipIndex`, `authMetadataIndex`, and `KVPairIndex`.

Add the storage and registry beans:

```kotlin
    /** Absent root: memory. Blank root: the start fails. See CHAT-ybtirmgj. */
    @Bean
    open fun luceneStorage(): LuceneStorage =
        LuceneStorages.of(root, keyType, nodeId).also { logger.info("lucene index storage: ${it.summary}") }

    @Bean
    open fun luceneIndexRegistry(): LuceneIndexRegistry = LuceneIndexRegistry(
        luceneStorage(),
        linkedMapOf(
            LuceneIndexNames.USER to userIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.MESSAGE to messageIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.TOPIC to topicIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.MEMBERSHIP to membershipIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.AUTH to authMetadataIndex() as LuceneIndex<*, *>,
            LuceneIndexNames.KEY_VALUE to KVPairIndex() as LuceneIndex<*, *>,
        ),
    )
```

Replace each of the six load beans and the `load` helper. Keep the existing KDoc on each load bean, and change its first sentence to say that the load opens the index against its store:

```kotlin
    @Bean
    open fun luceneUserIndexLoad(persistence: ObjectProvider<PersistenceServiceBeans<*, *>>): StartupIndexLoad =
        load(LuceneIndexNames.USER, userIndex(), persistence) { it.userPersistence() }

    // topic: topicIndex(), it.topicPersistence()
    // message: messageIndex(), it.messagePersistence()
    // membership: membershipIndex(), it.membershipPersistence()
    // auth: authMetadataIndex(), it.authMetaPersistence()
    // keyvalue: KVPairIndex(), it.keyValuePersistence()

    /** No store gives a null store. LuceneIndexLoad decides by the storage mode. */
    @Suppress("UNCHECKED_CAST")
    private fun load(
        name: String,
        index: Any,
        persistence: ObjectProvider<PersistenceServiceBeans<*, *>>,
        pick: (PersistenceServiceBeans<*, *>) -> PersistenceStore<*, *>,
    ): StartupIndexLoad = LuceneIndexLoad(
        index as LuceneIndex<T, Any>,
        name,
        luceneStorage(),
        persistence.ifAvailable?.let(pick) as PersistenceStore<T, Any>?,
    )
```

Write out all six load beans in full. The comment block above only names the pairs. Delete `PersistedIndexLoad` from the imports. `PersistedIndexLoad` stays in chat-core.

**Step 5: Run the module tests and see them pass**

```bash
mvn -o -B -pl chat-core,chat-index-lucene test > "$LOG" 2>&1; echo "exit=$?"; grep -E "Tests run:.*Fail|ERROR\]" "$LOG" | tail -12
```

Expected: `exit=0`. `LuceneIndexBeansConditionTests` still passes, because the new constructor parameters have defaults.

**Step 6: Run the memory deployment tests**

The memory deployment opens every index through `RootKeyStartup` now.

```bash
mvn -o -B -pl chat-deploy-memory -am test > "$LOG" 2>&1; echo "exit=$?"; grep -E "Tests run:.*Fail|ERROR\]|lucene index storage" "$LOG" | tail -12
```

Expected: `exit=0`, and `lucene index storage: memory` in the log.

If a test reports `is not open`, that test context builds a Lucene index and runs no index load. Read its configuration. Record the class name in an fp comment, and fix the context, not the lifecycle rule.

**Step 7: Commit**

```bash
git add \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/LuceneIndexLoad.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/LuceneIndexRegistry.kt \
  chat-index-lucene/src/main/kotlin/com/demo/chat/config/LuceneIndexBeans.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/LuceneIndexRegistryTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/LuceneIndexBeansStorageTests.kt \
  chat-index-lucene/src/test/kotlin/com/demo/chat/test/index/lucene/LuceneIndexLoadTests.kt
git commit -m "Open each Lucene index through its start load (CHAT-ybtirmgj)

LuceneIndexLoad opens one index against its store. With no store,
memory mode opens empty and files mode fails the start. The registry
reports the six indexes and writes operator requests. Each index bean
closes on shutdown.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The actuator endpoint

**Issue:** `CHAT-gnykibjr`. Start with `fp issue update --status in-progress CHAT-gnykibjr`. End with a comment and `fp issue update --status done CHAT-gnykibjr`.

**Files:**
- Create: `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/LuceneIndexEndpoint.kt`
- Test: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/LuceneIndexEndpointTests.kt` (four nested-free classes in one file)

**Interfaces:**
- Consumes: `IndexFileAdmin`, `IndexFileReport`, `IndexRequestResult` from Task 3, and the `luceneIndexRegistry` bean from Task 7.
- Produces: endpoint id `luceneindex`. `GET /actuator/luceneindex`, `POST /actuator/luceneindex/{name}`, `DELETE /actuator/luceneindex/{name}`.

**Step 1: Write the failing HTTP tests**

`chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/LuceneIndexEndpointTests.kt`:

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.deploy.memory.ChatApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.file.Files
import java.nio.file.Path

/** The launch surface of MemoryVectorIndexActuatorTests, without the vector selectors. */
private val BASE = arrayOf(
    "spring.application.name=test-deployment-lucene-actuator", "app.server.proto=rsocket",
    "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
    "app.service.core.key=memory", "app.service.core.pubsub=memory", "app.service.core.index=lucene",
    "app.service.core.persistence=memory", "app.service.core.secrets=memory",
    "app.service.composite", "app.service.composite.auth=true",
    "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
    "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
    "app.service.security.userdetails",
)

private const val WITH_DEFAULTS =
    "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml"
private const val WITHOUT_DEFAULTS =
    "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/userinit.yml"

abstract class LuceneEndpointClient {
    @Value("\${local.server.port}")
    private var port: Int = 0

    protected val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    protected fun WebTestClient.RequestHeadersSpec<*>.actuator() =
        headers { it.setBasicAuth("actuator", "actuator") }
}

/** Test 1. The shipped defaults keep the endpoint unexposed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@TestPropertySource(properties = [WITH_DEFAULTS])
class LuceneIndexEndpointDefaultsTests : LuceneEndpointClient() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) = BASE.forEach { p ->
            val (k, v) = p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") }
            registry.add(k) { v }
        }
    }

    @Test
    fun `the shipped defaults answer 404`() {
        client.get().uri("/actuator/luceneindex").actuator().exchange().expectStatus().isNotFound
    }
}

/**
 * Test 2. This test isolates the annotation. It sets no global access default
 * and no endpoint access, and it exposes the id. Boot 4.0.8 resolves both
 * annotation values to NONE when enabled-by-default=false is set, so the
 * shipped defaults cannot isolate the annotation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@TestPropertySource(properties = [WITHOUT_DEFAULTS, "management.endpoints.web.exposure.include=luceneindex"])
class LuceneIndexEndpointAnnotationTests : LuceneEndpointClient() {
    companion object {
        private val root: Path = Files.createTempDirectory("lucene-endpoint-annotation")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            BASE.forEach { p ->
                val (k, v) = p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") }
                registry.add(k) { v }
            }
            registry.add("app.index.lucene.root") { root.toString() }
        }
    }

    @Test
    fun `every operation answers 404, and no request file appears`() {
        client.get().uri("/actuator/luceneindex").actuator().exchange().expectStatus().isNotFound
        client.post().uri("/actuator/luceneindex/user").actuator().exchange().expectStatus().isNotFound
        client.delete().uri("/actuator/luceneindex/user").actuator().exchange().expectStatus().isNotFound
        assertThat(Files.exists(root.resolve("long/1/user/chat-rebuild.request"))).isFalse()
        assertThat(Files.exists(root.resolve("long/1/user/chat-drop.request"))).isFalse()
    }
}

/** Tests 3, 4 and 5. Access and exposure are both set, and the indexes are in files. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        WITH_DEFAULTS,
        "management.endpoint.luceneindex.access=unrestricted",
        "management.endpoints.web.exposure.include=luceneindex",
    ]
)
class LuceneIndexEndpointAccessTests : LuceneEndpointClient() {
    companion object {
        private val root: Path = Files.createTempDirectory("lucene-endpoint-access")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            BASE.forEach { p ->
                val (k, v) = p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") }
                registry.add(k) { v }
            }
            registry.add("app.index.lucene.root") { root.toString() }
        }
    }

    private fun request(index: String, file: String) = root.resolve("long/1/$index/$file")

    @Test
    fun `no credentials answer 401, and no request file appears`() {
        client.get().uri("/actuator/luceneindex").exchange().expectStatus().isUnauthorized
        client.post().uri("/actuator/luceneindex/topic").exchange().expectStatus().isUnauthorized
        client.delete().uri("/actuator/luceneindex/topic").exchange().expectStatus().isUnauthorized
        assertThat(Files.exists(request("topic", "chat-rebuild.request"))).isFalse()
        assertThat(Files.exists(request("topic", "chat-drop.request"))).isFalse()
    }

    @Test
    fun `the read answers six reports`() {
        client.get().uri("/actuator/luceneindex").actuator().exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.length()").isEqualTo(6)
            .jsonPath("$[0].mode").isEqualTo("files")
    }

    @Test
    fun `post writes a rebuild request`() {
        client.post().uri("/actuator/luceneindex/user").actuator().exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.accepted").isEqualTo(true)
        assertThat(Files.exists(request("user", "chat-rebuild.request"))).isTrue()
    }

    @Test
    fun `delete writes a drop request`() {
        client.delete().uri("/actuator/luceneindex/message").actuator().exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.accepted").isEqualTo(true)
        assertThat(Files.exists(request("message", "chat-drop.request"))).isTrue()
    }

    @Test
    fun `an unknown name lists the indexes`() {
        client.post().uri("/actuator/luceneindex/nope").actuator().exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.accepted").isEqualTo(false)
            .jsonPath("$.reason").value<String> { assertThat(it).contains("user, message, topic, membership, auth, keyvalue") }
    }
}

/** Test 6. Memory mode refuses both commands. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        WITH_DEFAULTS,
        "management.endpoint.luceneindex.access=unrestricted",
        "management.endpoints.web.exposure.include=luceneindex",
    ]
)
class LuceneIndexEndpointMemoryTests : LuceneEndpointClient() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) = BASE.forEach { p ->
            val (k, v) = p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") }
            registry.add(k) { v }
        }
    }

    @Test
    fun `both commands are refused in memory mode`() {
        client.post().uri("/actuator/luceneindex/user").actuator().exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.accepted").isEqualTo(false)
            .jsonPath("$.reason").value<String> { assertThat(it).contains("in memory") }
        client.delete().uri("/actuator/luceneindex/user").actuator().exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.accepted").isEqualTo(false)
    }
}
```

Before Step 2, read the `ChatApp` import in `MemoryVectorIndexActuatorTests.kt` and use the same one. A property with no value in `BASE`, such as `app.service.composite`, must register as an empty string, which the `split` above does.

**Step 2: Run the tests and see them fail**

```bash
mvn -o -B -pl chat-deploy-memory -am -Dtest='LuceneIndexEndpoint*' -Dsurefire.failIfNoSpecifiedTests=false test > "$LOG" 2>&1; echo "exit=$?"; grep -E "Tests run:|FAIL" "$LOG" | tail -10
```

Expected: `exit=1`. The access tests fail with 404, because no endpoint exists. The defaults and annotation tests can pass already. That is expected, and the mutation in Step 5 proves the annotation test.

**Step 3: Write the endpoint**

`chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/LuceneIndexEndpoint.kt`:

```kotlin
package com.demo.chat.config.deploy.actuator

import com.demo.chat.service.core.IndexFileAdmin
import com.demo.chat.service.core.IndexFileReport
import com.demo.chat.service.core.IndexRequestResult
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.endpoint.Access
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.Selector
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * The operator view of the Lucene indexes, and the rebuild and drop requests.
 * A request takes effect at the next start. See CHAT-ybtirmgj.
 *
 * Access is NONE by default. Boot 4.0.8 defaults the annotation to
 * UNRESTRICTED, so this class sets NONE explicitly. An operator enables it
 * with two properties:
 *
 * ```
 * management.endpoint.luceneindex.access=unrestricted
 * management.endpoints.web.exposure.include=luceneindex
 * ```
 *
 * No deployment sets either. `ActuatorWebSecurityConfiguration` requires the
 * ACTUATOR role on every actuator route except health.
 *
 * The condition matches `LuceneIndexBeans`. A conditional on the registry bean
 * would depend on registration order, so the admin arrives through a provider.
 */
@Component
@Endpoint(id = "luceneindex", defaultAccess = Access.NONE)
@ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene", matchIfMissing = true)
class LuceneIndexEndpoint(private val admin: ObjectProvider<IndexFileAdmin>) {

    @ReadOperation
    fun reports(): List<IndexFileReport> = admin.ifAvailable?.reports() ?: emptyList()

    @WriteOperation
    fun rebuild(@Selector name: String): IndexRequestResult =
        admin.ifAvailable?.requestRebuild(name) ?: NO_INDEX

    @DeleteOperation
    fun drop(@Selector name: String): IndexRequestResult =
        admin.ifAvailable?.requestDrop(name) ?: NO_INDEX

    companion object {
        private val NO_INDEX = IndexRequestResult(false, "This process holds no Lucene index.", null)
    }
}
```

**Step 4: Run the tests and see them pass**

Run the Step 2 command again.
Expected: `exit=0`. Four classes, 8 tests, 0 failures.

**Step 5: Run the annotation mutation**

Edit `LuceneIndexEndpoint.kt`: change `Access.NONE` to `Access.UNRESTRICTED`. Run the Step 2 command.
Expected: `exit=1`. `LuceneIndexEndpointAnnotationTests` fails.
Restore by absolute path and prove it:

```bash
git add /Users/darkbit1001/workspace/demo-chat/chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/LuceneIndexEndpoint.kt   # before the mutation
git checkout -- /Users/darkbit1001/workspace/demo-chat/chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/LuceneIndexEndpoint.kt
git diff --stat
```

Expected: no output from `git diff --stat`.

**Step 6: Commit**

```bash
git add \
  chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/LuceneIndexEndpoint.kt \
  chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/LuceneIndexEndpointTests.kt
git commit -m "Add the luceneindex actuator endpoint (CHAT-ybtirmgj)

The endpoint reads the six index reports and writes rebuild and drop
requests. Its access is NONE by default. One test isolates the
annotation with no global access default, because the shipped default
resolves both annotation values to NONE.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Redis restart with index files

**Issue:** `CHAT-lztsrkev`. Start with `fp issue update --status in-progress CHAT-lztsrkev`. End with a comment and `fp issue update --status done CHAT-lztsrkev`.

**Files:**
- Create: `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisLuceneFilesRestartTests.kt`
- Modify: `docs/NODEID-CLAIM.md:145` (the allocation table)

**Interfaces:**
- Consumes: `IndexFileAdmin` bean, `RedisDeployBootTests.redis`, `RedisDeployBootTests.BootApp`.

**Step 1: Write the test**

```kotlin
package com.demo.chat.test.deploy.redis

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.NodeIdClaimException
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.core.IndexFileAdmin
import com.demo.chat.service.core.UserIndexService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.nio.file.Path
import java.time.Duration

/**
 * The Lucene indexes of a Redis deployment in files. A restart with intact
 * files reuses them. A store write with no index write makes the next start
 * build. See CHAT-ybtirmgj.
 *
 * Node ids 18 and 19 belong to this class. See docs/NODEID-CLAIM.md. Each test
 * restarts on one node id, because the index path holds the node id. A Redis
 * close does not release the claim (CHAT-ocpojbyy), so the claim TTL is 3
 * seconds and the second start retries until the claim is free.
 */
@Tag("integration")
class RedisLuceneFilesRestartTests {

    private val timeout = Duration.ofSeconds(10)

    companion object {
        private val container = RedisDeployBootTests.redis

        /** A null root keeps the indexes in memory. */
        private fun properties(root: Path?, nodeId: Int) = arrayOf(
            "spring.application.name=redis-lucene-files-test",
            "spring.config.additional-location=classpath:/config/userinit.yml",
            "server.port=0", "spring.rsocket.server.port=0", "app.server.proto=rsocket",
            "app.key.type=long", "app.nodeid=$nodeId", "app.users.create=true",
            "app.service.core.key=redis", "app.service.core.persistence=redis",
            "app.service.core.pubsub=redis-pubsub", "app.service.core.index=lucene",
            "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth=true",
            "app.controller.persistence", "app.controller.index", "app.controller.key", "app.controller.pubsub",
            "app.controller.secrets", "app.controller.user", "app.controller.topic", "app.controller.message",
            "app.service.security.userdetails",
            "spring.cloud.consul.enabled=false", "spring.cloud.consul.discovery.enabled=false",
            "spring.cloud.consul.config.enabled=false",
            "redis-topics.host=${container.containerIpAddress}",
            "redis-topics.port=${container.getMappedPort(6379)}",
            "app.nodeid.claim.ttl=3s", "app.nodeid.claim.renew-interval=1s",
            "app.nodeid.claim.safety-margin=1s", "app.nodeid.claim.operation-timeout=500ms",
        ).toList() + listOfNotNull(root?.let { "app.index.lucene.root=$it" })

        fun start(root: Path?, nodeId: Int): ConfigurableApplicationContext =
            SpringApplicationBuilder(RedisDeployBootTests.BootApp::class.java)
                .web(WebApplicationType.NONE)
                .properties(*properties(root, nodeId).toTypedArray())
                .run()

        /** Retries only on a held claim, and for at most 15 seconds. Any other failure ends the test. */
        fun startWhenClaimFree(root: Path?, nodeId: Int): ConfigurableApplicationContext {
            val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
            while (true) {
                try {
                    return start(root, nodeId)
                } catch (e: Exception) {
                    val claimed = generateSequence<Throwable>(e) { it.cause }.any { it is NodeIdClaimException }
                    if (!claimed || System.nanoTime() > deadline) throw e
                    Thread.sleep(500)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun ConfigurableApplicationContext.addUser(handle: String) {
            val composite = getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
            composite.userService().addUser(UserCreateRequest("files", handle, "http://u")).block(Duration.ofSeconds(10))
        }
    }

    private fun ConfigurableApplicationContext.report(name: String) =
        getBean(IndexFileAdmin::class.java).reports().single { it.name == name }

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.findsHandle(handle: String): Boolean =
        (getBean("userIndex") as UserIndexService<Long, IndexSearchRequest>)
            .findBy(IndexSearchRequest("handle", handle, 10)).collectList().block(timeout)!!.isNotEmpty()

    @Test
    fun `a restart with intact files reuses them`(@TempDir root: Path) {
        startWhenClaimFree(root, 18).use { first -> first.addUser("filesreuse") }
        startWhenClaimFree(root, 18).use { second ->
            val user = second.report("user")
            assertThat(user.outcome).isEqualTo("REUSED")
            assertThat(user.documentsWritten).isEqualTo(0)
            assertThat(second.findsHandle("filesreuse")).isTrue()
        }
    }

    @Test
    fun `a store write with no index write makes the next start build`(@TempDir root: Path) {
        startWhenClaimFree(root, 19).use { first ->
            first.addUser("filesfirst")
            @Suppress("UNCHECKED_CAST")
            val stores = first.getBean(PersistenceServiceBeans::class.java) as PersistenceServiceBeans<Long, String>
            val key = stores.userPersistence().key().block(timeout)!!
            stores.userPersistence().add(User.create(key, "files", "filesstoreonly", "http://u")).block(timeout)
            assertThat(first.findsHandle("filesstoreonly")).isFalse()
        }
        startWhenClaimFree(root, 19).use { second ->
            val user = second.report("user")
            assertThat(user.outcome).isEqualTo("BUILT")
            assertThat(user.reason).isEqualTo("MISMATCH")
            assertThat(second.findsHandle("filesstoreonly")).isTrue()
            assertThat(second.findsHandle("filesfirst")).isTrue()
        }
    }
}
```

The companion holds `start`, `startWhenClaimFree` and `addUser`, so the Task 10 probe can reuse them. Before Step 2, check four names with the language server and fix the imports: `CompositeServiceBeans`, `UserCreateRequest`, `NodeIdClaimException`, and `PersistenceServiceBeans`. `RedisGrantRestartTests.kt` imports the first two, so copy its lines.

A different Redis test writes into the same container. The second start compares every stored user with the index files of node id 18 or 19. Users that another test class wrote earlier are in the store and also in the index files, because the first start of this test indexed them. So they do not break the reuse test.

**Step 2: Update the node id table**

In `docs/NODEID-CLAIM.md`, add a row after the `RedisGrantRestartTests` row:

```
| `chat-deploy-redis` `RedisLuceneFilesRestartTests` | 18 and 19 |
```

**Step 3: Run the test**

```bash
mvn -o -B -pl chat-deploy-redis -am -Pintegration -Dtest=RedisLuceneFilesRestartTests -Dsurefire.failIfNoSpecifiedTests=false verify > "$LOG" 2>&1; echo "exit=$?"; grep -E "Tests run:|FAIL|lucene index user" "$LOG" | tail -12
```

Expected: `exit=0`, `Tests run: 2, Failures: 0`. The log holds `lucene index user: reused, ...` and `lucene index user: built, reason=MISMATCH, ...`.

Before a container run, check that no other session runs container tests on this Docker VM. After a red run, read `docker events --since 30m --until 0s --filter event=oom` before you trust the result.

**Step 4: Commit**

```bash
git add \
  chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisLuceneFilesRestartTests.kt \
  docs/NODEID-CLAIM.md
git commit -m "Prove Lucene index files across a Redis restart (CHAT-ybtirmgj)

A restart with intact files reuses them and writes no document. A
Redis write with no index write makes the next start build, and the
user is found after the start. Node ids 18 and 19 belong to the test.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Measure the fsync cost

**Issue:** `CHAT-lnvtyvyv`. Start with `fp issue update --status in-progress CHAT-lnvtyvyv`. End with a comment and `fp issue update --status done CHAT-lnvtyvyv`.

**Files:** none committed. The numbers go to the issue.

The approved check is one Redis-backed write, measured with memory indexes and with file indexes. The write is `userService().addUser`, which writes the Redis store and then the Lucene user index. An isolated Lucene measurement is supplementary evidence only.

**Step 1: Write a temporary Redis probe**

`chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisIndexWriteCostProbe.kt`, not committed:

```kotlin
package com.demo.chat.test.deploy.redis

import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.addUser
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.startWhenClaimFree
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ConfigurableApplicationContext
import java.nio.file.Path

/** Temporary. Node ids 18 and 19, as in RedisLuceneFilesRestartTests. Not committed. */
@Tag("integration")
class RedisIndexWriteCostProbe {

    /** Microseconds per write, over 200 writes after 20 warm-up writes. */
    private fun ConfigurableApplicationContext.perWrite(run: Int, mode: String): Long {
        (1..20).forEach { addUser("warm${mode}r${run}n$it") }
        val start = System.nanoTime()
        (1..200).forEach { addUser("probe${mode}r${run}n$it") }
        return (System.nanoTime() - start) / 1000 / 200
    }

    @Test
    fun measure(@TempDir root: Path) {
        (1..3).forEach { run ->
            val memory = startWhenClaimFree(null, 18).use { it.perWrite(run, "m") }
            val files = startWhenClaimFree(root.resolve("run$run"), 19).use { it.perWrite(run, "f") }
            println("WRITE-PROBE run=$run memory_us_per_write=$memory files_us_per_write=$files")
        }
    }
}
```

**Step 2: Run the Redis probe**

```bash
mvn -o -B -pl chat-deploy-redis -am -Pintegration -Dtest=RedisIndexWriteCostProbe -Dsurefire.failIfNoSpecifiedTests=false verify > "$LOG" 2>&1; echo "exit=$?"; grep -o "WRITE-PROBE.*" "$LOG"
```

Expected: `exit=0`, and three `WRITE-PROBE` lines.

**Step 3: Supplementary isolated measurement**

`LTEST/memory/FsyncCostProbe.kt`, not committed:

```kotlin
package com.demo.chat.test.memory

import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.FileStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Flux
import java.nio.file.Path

class FsyncCostProbe {
    private fun index() = LuceneIndex<Long, Long>({ listOf("v" to it.toString()) }, { Key.of(it.toLong(), -9L) }, { Key.of(it, -9L) })

    private fun perAdd(index: LuceneIndex<Long, Long>): Long {
        (1L..50L).forEach { index.add(it).block() }
        val start = System.nanoTime()
        (51L..1050L).forEach { index.add(it).block() }
        return (System.nanoTime() - start) / 1000 / 1000
    }

    @Test
    fun measure(@TempDir root: Path) {
        val memory = index().apply { openInMemory("probe") }
        val files = index().apply { open("probe", FileStorage(root, "long", 1)) { Flux.empty() } }
        try {
            println("FSYNC-PROBE memory_us_per_add=${perAdd(memory)} files_us_per_add=${perAdd(files)}")
        } finally {
            memory.close()
            files.close()
        }
    }
}
```

```bash
for i in 1 2 3; do lucene_test 'FsyncCostProbe' >/dev/null; grep -o "FSYNC-PROBE.*" "$LOG"; done
```

**Step 4: Delete both probes, prove it, and report**

```bash
rm /Users/darkbit1001/workspace/demo-chat/chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisIndexWriteCostProbe.kt \
   /Users/darkbit1001/workspace/demo-chat/chat-index-lucene/src/test/kotlin/com/demo/chat/test/memory/FsyncCostProbe.kt
git status --short
fp comment CHAT-ybtirmgj "Write cost of userService().addUser on Redis, 200 writes after 20 warm-up writes, three runs: <paste the WRITE-PROBE lines>. Supplementary isolated Lucene adds: <paste the FSYNC-PROBE lines>. Report only. A batching change is a separate issue."
```

Expected: `git status --short` prints nothing.

---

### Task 11: Documents, drift, and the gate

**Issue:** `CHAT-ndgafauz`. Start with `fp issue update --status in-progress CHAT-ndgafauz`. End with a comment and `fp issue update --status done CHAT-ndgafauz`.

**Files:**
- Create: `docs/LUCENE-INDEX-FILES.md`
- Modify: `docs/ARCHITECTURE.md:132`, `docs/ARCHITECTURE.md:190`, `docs/BUILD-HEALTH.md`
- Modify: `drift.lock` through `drift link`

**Step 1: Write the operator note**

`docs/LUCENE-INDEX-FILES.md`:

````markdown
# Lucene index files

Issue `CHAT-ybtirmgj`. Spec:
`docs/superpowers/specs/2026-10-06-lucene-index-files-design.md`.

## Where the files live

Set `app.index.lucene.root` to keep the Lucene indexes in files. Without it,
every index stays in memory, and each start builds it from the store. A blank
value fails the start.

```
<root>/<app.key.type>/<app.nodeid>/<index>
index = user | message | topic | membership | auth | keyvalue
```

Each index directory holds `chat-owner.lock` while a process uses it. A second
process with the same key type and node id fails its start.

## What a start does

A start compares each stored entity with the index, byte for byte. Equal
content reuses the files. A missing, damaged or different index is built from
the store, with one commit. The start logs one line per index:

```
lucene index storage: files at /var/lib/chat/lucene
lucene index user: reused, 42 entries compared, 0 written
lucene index user: built, reason=MISMATCH, 42 entries written
```

| Reason | Meaning |
|---|---|
| `NO_COMMIT` | The directory holds no index. |
| `HEADER` | The format, Lucene version, analyzer, key type, node id or index name differs. |
| `MISMATCH` | An entry is missing, extra or different. |
| `DAMAGE` | A file is damaged. The start deleted the index files, then built. |
| `REQUESTED_REBUILD` | An operator requested a rebuild. |
| `REQUESTED_DROP` | An operator requested a drop. |

The compare is exact only when no other process writes the store during the
start. `CHAT-lswjobhz` holds that limit.

> A node can retain a stale index after another node changes the shared
> store. At restart, differing indexed content causes a rebuild.

### Failures before any file changes

The start fails and leaves the files as they were when:

- another process holds `chat-owner.lock` or `write.lock`
- the root is set and the process has no local store
- a permission or a storage error occurs before recovery starts, for example
  a full disk
- the store or the encoder fails during the compare
- the store emits one key twice during the compare
- a writer rollback fails before recovery deletes anything

### Failures after the start begins to change the files

- **Recovery** runs for a damaged index or a drop request. It deletes the index
  files first. A later failure leaves only the lock files and the request files.
  The next start builds.
- **A build on the header, mismatch or rebuild path** keeps the previous commit
  when its rollback succeeds.
- **A failed build commit** leaves the committed state uncertain.
- **A failure after the commit**, in request deletion, the directory sync or
  the searcher, leaves the new commit in place.

In each case the next start checks the files again.

## The endpoint

The `luceneindex` actuator endpoint is off by default. Enable it with:

```
management.endpoint.luceneindex.access=unrestricted
management.endpoints.web.exposure.include=luceneindex
```

Every call needs the actuator credentials.

| Call | Effect |
|---|---|
| `GET /actuator/luceneindex` | The state of the six indexes |
| `POST /actuator/luceneindex/{name}` | Request a rebuild at the next start |
| `DELETE /actuator/luceneindex/{name}` | Request a drop at the next start |

A request changes nothing in the running process. In memory mode both requests
are refused, because every start already builds.

`accepted=true` means the request file is durable. `accepted=false` with the
words "can remain pending" means the request file is in place and the directory
sync failed. The next start can still act on it.

## Recovery

1. **Restart.** A start finds a damaged or different index and builds it.
2. **Rebuild request.** The next start builds. The previous commit stays until
   the new one is committed.
3. **Drop request.** The next start deletes the index files, then builds. Use it
   when you do not trust the files, beyond what the start checks.
4. **Offline delete.** Stop the process. Delete
   `<root>/<keyType>/<nodeId>/<index>`. Start the process.

To cancel a request, stop the process, and delete `chat-rebuild.request` or
`chat-drop.request` from the index directory. No command cancels a request.

Never delete `chat-owner.lock` or `write.lock` while a process runs.
````

**Step 2: Update `docs/ARCHITECTURE.md`**

Replace the sentence at line 190 with:

```markdown
**Every Lucene index opens before readiness.** Without `app.index.lucene.root`, each index lives in process memory, and each start builds it from its store. With the root, each index lives in `<root>/<keyType>/<nodeId>/<index>`. A start reuses the files when every stored entity matches its document byte for byte, and builds otherwise. Six indexes open this way: auth, user, topic, message, membership and key-value. A store error, a repeated store key or a lock held elsewhere fails the start. A `NONE` process opens no index. The compare is exact only when no other process writes the store during the start (`CHAT-lswjobhz`). Two concurrent updates of one key can still leave a stale entry until the next start (`CHAT-oltrsgws`). See `docs/LUCENE-INDEX-FILES.md`.
```

Line 132 names `LoadablePersistedIndex`. Check whether that type still exists with `mcp__treesitter-mcp__find_usages`. If it does not exist, replace the line with:

```markdown
- `LuceneIndexLoad` — opens one Lucene index at boot, against its store. It reuses index files that match the store, and builds otherwise.
```

**Step 3: Bind the operator note with drift**

Review the prose of `docs/LUCENE-INDEX-FILES.md` against the code first. Then:

```bash
cd /Users/darkbit1001/workspace/demo-chat
drift link docs/LUCENE-INDEX-FILES.md chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/impl/LuceneIndex.kt
drift link docs/LUCENE-INDEX-FILES.md chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexStartSequence.kt
drift link docs/LUCENE-INDEX-FILES.md chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexRequests.kt
drift link docs/LUCENE-INDEX-FILES.md chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/LuceneIndexLoad.kt
drift link docs/LUCENE-INDEX-FILES.md chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/LuceneIndexRegistry.kt
drift link docs/LUCENE-INDEX-FILES.md chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/LuceneIndexEndpoint.kt
drift status
drift check
```

Expected: `drift status` lists `docs/LUCENE-INDEX-FILES.md` with six bindings. `drift check` reports `ok`. Bind whole files. A Kotlin symbol anchor fails on drift v0.7.0.

**Step 4: Run the CI gate**

```bash
DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > "$LOG" 2>&1; echo "exit=$?"; tail -30 "$LOG"
```

Expected: `exit=0`, no drift, and `agent http gate: ok`. Read the image id before and after with `docker image inspect --format '{{.Id}}' docker.io/library/chat-deploy-long-memory-integration-test:0.0.1`. The id must change, or the shell tests ran against an old image.

If the run fails, read `docker events --since 60m --until 0s --filter event=oom` first.

**Step 5: Record the counts**

Update the test counts in `docs/BUILD-HEALTH.md` from the default run and the `--ci` run. Run `shell-scripts/build-health.sh` once in default mode for its count:

```bash
shell-scripts/build-health.sh > "$LOG" 2>&1; echo "exit=$?"; tail -15 "$LOG"
```

The module lists in `shell-scripts/build-health-tests-*.txt` do not change, because this work adds no module.

**Step 6: Commit and log**

```bash
git add docs/LUCENE-INDEX-FILES.md docs/ARCHITECTURE.md docs/BUILD-HEALTH.md drift.lock
git commit -m "Document the Lucene index files (CHAT-ybtirmgj)

The operator note states where the files live, what each start reason
means, how to enable the endpoint, and how to recover. drift binds it
to the six source files that it describes.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-ybtirmgj "All tasks done on chat-ybtirmgj-lucene-files. build-health.sh --ci: <paste counts>. Default: <paste counts>. Image id <old> to <new>. drift check ok."
```

---

## Spec coverage

| Spec section | Task |
|---|---|
| Configuration and layout, blank root | 4, 7 |
| Files in one index directory, reserved names | 2, 3, 5 |
| The document, `_enc`, reserved fields | 2 |
| Canonical encoding, header | 2 |
| Start sequence, compare, integrity, damage class | 5 |
| Recovery under the lock | 5 |
| Build, pre-commit checks, failure classes | 5 |
| Start outcome and log lines | 2, 6 |
| Lifecycle, mutations, failures, readers, close | 6 |
| Operator commands, durability | 3, 7 |
| Code placement, endpoint | 3, 7, 8 |
| Tests, mutations, gate | 2 to 9, 11 |
| Documents, drift | 11 |
| Open checks: no-store audit, fsync cost | 1, 10 |
