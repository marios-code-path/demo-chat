# The target domain scan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **A project rule forbids subagent-driven development.** `AGENTS.md` states
> "NEVER use sub-agent driven development!" twice, in bold. So the only
> permitted sub-skill here is `superpowers:executing-plans`. Do not dispatch a
> subagent for a task.

**Goal:** A permission check reads the given target and the domain root of that
target, so a grant on a domain root reaches an object of that domain.

**Architecture:** One private helper builds the target list. Two permission
reads use it, and two owner-selection reads do not. The owner selection stays
exact, because a domain root read there would give one target two owners.

**Tech Stack:** Kotlin 2.4.20, JDK 25, Spring Boot 4.0.8, Maven reactor, Reactor,
JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-30-target-domain-scan-design.md`

**Issue:** `CHAT-rfzsnbco`. The branch is `chat-rfzsnbco-target-domain-scan`.

## Global Constraints

- **Preserve the exact-target owner rule.** `getAuthorizationsForTarget` and
  `getAuthorizationsForMultipleTarget` must not read the domain root.
- A key carries `id`, `root` and `empty`. `Key.root(id)` builds a root key, and
  a root key is its own root. `Key.of(id, rootId)` builds an object key.
- An identity is a user. `Key.of(id, userRootId)`.
- `WILDCARD` is the string `*`. It means ownership, it is singular per target,
  and it is a sentinel. `ALL` is a literal permission string and is never a
  wildcard.
- The field of a stored row is named `permission`. Do not write `role` for it.
  `role` is the key name of a row in `userinit.yml` alone.
- **Do not touch the unrelated working-tree edits.** `userinit.yml` and
  `CompositeControllersConfiguration.kt` carry uncommitted edits that belong to
  another line of work. Stage named paths only. Never run `git add -A` and
  never run `git add .`.
- Every commit message ends with the trailer
  `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- Every commit message names `CHAT-rfzsnbco`.
- Use Controlled English for every prose change. One instruction per sentence.
  No more than 20 words per instruction and 25 words per description.
- **Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone.** A
  single-module run resolves `chat-core` from `~/.m2` and reports failures that
  are not real.
- Log long build output to a file. Read the exit code and the summary lines.

## Review Focus

Each line names an input or a condition that the spec implies and that no other
task's tests exercise. The owning task carries the test.

1. **A check of a key against itself.** `AuthMetadataAccessBroker` answers allow
   before it reads a row, so the scan must not run and the index must stay
   unread. Task 3.
2. **A check with no permission.** `getAuthorizationsAgainst` accepts a null
   permission. The scan must still read both targets. Task 3.
3. **A target whose root is not a registered domain root.** The scan adds that
   root key as a target anyway. It must match no row, and it must not throw.
   Task 3.
4. **A repeated target in a many-target list.** Each occurrence is read through
   its own check. Task 4.
5. **An empty many-target list.** It must read no target and answer nothing.
   Task 4.

---

### Task 1: The fixture carries the domain root of each key

The test keys of `AnonymousAuthorizationMatrixTests` all carry the fixed root
`TestRoots.LONG` of `-9L`. So no key carries the root of its own domain, and a
room key cannot reach the `MessageTopic` root. **The scan cannot be measured
through that fixture.**

This task changes the fixture alone. The change is behaviour-neutral, and the
existing suite must stay green. That green is the evidence that the fixture
moved nothing.

**Files:**
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`

**Interfaces:**
- Consumes: `Key.of(id, root)`, `Key.root(id)`, `RootKeysFixture.ofLong`,
  `TestVerifiers.holding`.
- Produces: the companion keys `ANON_KEY`, `ADMIN_KEY`, `USER_ROOT`,
  `MESSAGE_ROOT`, `TOPIC_ROOT`, `CALLER_KEY`, `ROOM_KEY`, `MESSAGE_KEY`, each
  under its own domain root. Every later task reads these.

- [ ] **Step 1: Replace the companion key constants**

Replace the whole `private companion object` block, at lines 418 to 428, with
this block.

```kotlin
    private companion object {
        val nextKey = AtomicLong(100L)

        /** The `User` domain root. A root key is its own root. */
        val USER_ROOT: Key<Long> = Key.root(3L)

        /** The `Message` domain root. */
        val MESSAGE_ROOT: Key<Long> = Key.root(4L)

        /** The `MessageTopic` domain root. */
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        /** The `Admin` identity. An identity is an object of the `User` domain. */
        val ADMIN_KEY: Key<Long> = Key.of(2L, 3L)

        /** The `Anon` identity. It is an object of the `User` domain too. */
        val ANON_KEY: Key<Long> = Key.of(1L, 3L)

        /** An ordinary user, and the caller of most contexts of this test. */
        val CALLER_KEY: Key<Long> = Key.of(6L, 3L)

        /** A room. Its root is the `MessageTopic` root. */
        val ROOM_KEY: Key<Long> = Key.of(7L, 5L)

        /** A message. Its root is the `Message` root. */
        val MESSAGE_KEY: Key<Long> = Key.of(8L, 4L)
    }
```

`TestKeys.key` stays imported, because `MapAuthStore.key` still uses it for
grant keys. A grant key carries no domain, so its root does not matter here.

- [ ] **Step 2: Run the whole test class and confirm it still passes**

Run:

```sh
cd /Users/darkbit1001/workspace/demo-chat
mvn -o -B -pl chat-core,chat-security test \
  -Dtest=AnonymousAuthorizationMatrixTests \
  -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t1.log 2>&1
echo "exit=$?"; grep -E "Tests run:|BUILD" /tmp/t1.log | tail -5
```

Expected: `exit=0`, `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`.

The count may differ. What matters is `Failures: 0` and `Errors: 0`. **A failure
here means the fixture change is not behaviour-neutral.** Stop and report it. Do
not adjust an assertion to make it pass.

- [ ] **Step 3: Correct the stale count in one class comment**

The comment above `an authenticated caller reaches the same answers` says the
`user: User` rows are four, and that they reach nobody. Both are wrong. Replace
the paragraph at lines 84 to 88.

```kotlin
    /**
     * **An authenticated caller reaches the same answers as an anonymous
     * one.** `CoreAuthorizationService` puts the `Anon` key in the actor set
     * of every query, so an anonymous grant is a floor for every caller.
     *
     * The five `user: User` rows of `userinit.yml` name the `User` root key as
     * the principal. `CHAT-mahevldm` puts that key in the actor set of every
     * query, because every caller is a user. So all five reach every caller.
     */
```

**Do not change the `Message:GET` sentence in the comment above
`an anonymous caller may find a user and nothing else` in this task.** That
sentence is still true at the end of this task. Task 2 corrects it.

- [ ] **Step 4: Re-run the test class**

Run the same command as Step 2. Expected: `exit=0`, `Failures: 0`,
`Errors: 0`.

- [ ] **Step 5: Commit**

```sh
cd /Users/darkbit1001/workspace/demo-chat
git add chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt
git commit -m "Carry the domain root of each key in the matrix fixture (CHAT-rfzsnbco)

The fixture built every key through TestKeys.key, which gives one fixed
root. So no test key carried the root of its own domain, and the target
domain scan could not be measured.

The change is behaviour-neutral. The whole class stays green.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: A permission check reads the given target and its domain root

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/service/CoreAuthorizationService.kt:122-127`
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`

**Interfaces:**
- Consumes: the fixture keys of Task 1.
- Produces: `private fun targets(uidB: Key<T>): List<Key<T>>` in
  `CoreAuthorizationService`. Task 3 and Task 4 also use it.

- [ ] **Step 1: Write the failing tests**

Add these two tests to `AnonymousAuthorizationMatrixTests`, above the
`private fun permitted` helper at line 276.

```kotlin
    /**
     * **A grant on a domain root covers an object of that domain.**
     * `messageById` checks one message key with `GET`. The domain root of a
     * message key is the `Message` root, and `{Anon, Message, GET}` names that
     * root. `CHAT-rfzsnbco` makes the check read the root.
     */
    @Test
    fun `a message read allows through a domain root row`() {
        val row = grant(ANON_KEY, MESSAGE_ROOT, "GET")
        val service = SpringSecurityAccessBrokerService(broker(listOf(row)), rootKeys(), registry())

        val answer = service.hasAccessTo(MESSAGE_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(anonymousContext())))
            .block() ?: false

        assertThat(answer).describedAs("a message read").isTrue()
    }

    /**
     * **A room read allows through a domain root row.** The domain root of a
     * room key is the `MessageTopic` root, and `{User, MessageTopic, GET}`
     * names that root.
     */
    @Test
    fun `a room read allows through a domain root row`() {
        val row = grant(USER_ROOT, TOPIC_ROOT, "GET")
        val service = SpringSecurityAccessBrokerService(broker(listOf(row)), rootKeys(), registry())

        val answer = service.hasAccessTo(ROOM_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).describedAs("a room read").isTrue()
    }

    /**
     * **The scan does not widen across domains.** The domain root of a room key
     * is the `MessageTopic` root. The shipped `{User, Message, SEND}` row names
     * the `Message` root, which is a different domain. So `send` stays denied.
     */
    @Test
    fun `a send stays denied because the row names another domain`() {
        val rows = listOf(
            grant(USER_ROOT, MESSAGE_ROOT, "SEND"),
            grant(USER_ROOT, TOPIC_ROOT, "ALL")
        )
        val service = SpringSecurityAccessBrokerService(broker(rows), rootKeys(), registry())

        val answer = service.hasAccessTo(ROOM_KEY, "SEND")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).describedAs("a send to a room").isFalse()
    }
```

- [ ] **Step 2: Move the measured matrix row, and correct its comment**

In `operations()`, the row `"messageById GET"` keeps its expression. The
expectation moves.

Replace the assertion body of `an anonymous caller may find a user and nothing
else`, at lines 67 to 79.

```kotlin
    @Test
    fun `an anonymous caller may find a user and nothing else`() {
        assertThat(matrixFor(anonymousContext())).isEqualTo(
            mapOf(
                "addRoom MessageTopic NEW" to false,
                "send room SEND" to false,
                "whoami User FIND" to true,
                "messageById GET" to true,
                "listRooms MessageTopic ALL" to true,
                "addUser User NEW" to false
            )
        )
    }
```

Replace its comment, at lines 59 to 66.

```kotlin
    /**
     * **An anonymous caller may read a user and a message.**
     *
     * `userinit.yml` grants the `Anon` key `User:FIND`, `User:PUT` and
     * `Message:GET`. The two `User` grants reach `whoami`. `Message:GET` names
     * the `Message` root, and `messageById` checks one message key. Since
     * `CHAT-rfzsnbco` the check reads that root, so the row applies.
     */
```

- [ ] **Step 3: Run the tests and confirm they fail**

Run:

```sh
cd /Users/darkbit1001/workspace/demo-chat
mvn -o -B -pl chat-core,chat-security test \
  -Dtest=AnonymousAuthorizationMatrixTests \
  -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t2.log 2>&1
echo "exit=$?"; grep -E "Tests run:|BUILD" /tmp/t2.log | tail -5
```

Expected: FAIL. The four tests above fail with `expected: true but was: false`,
and the matrix test fails on the `messageById GET` entry.

- [ ] **Step 4: Write the minimal implementation**

In `CoreAuthorizationService.kt`, add this private method above
`getAuthorizationsForMultipleTarget`, at line 97.

```kotlin
    /**
     * The targets that a permission check reads: the given target, and the
     * domain root of that target.
     *
     * A root key is its own root, so a check that already names a domain root
     * reads one target. The list is distinct for that reason. See
     * `CHAT-rfzsnbco`.
     *
     * **The owner selection does not use this method.** A domain root read
     * there would give one target two owners.
     */
    private fun targets(uidB: Key<T>): List<Key<T>> {
        val root = Key.root(uidB.root)
        return if (root == uidB) listOf(uidB) else listOf(uidB, root)
    }
```

Replace the body of `getAuthorizationsAgainst`, at lines 122 to 127.

```kotlin
    override fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>, permission: String?): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            Flux.concat(targets(uidB).map { authIndex.findBy(queryForTarget.apply(it)).flatMap(authPersist::get) }),
            actors(uidA),
            permission
        )
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run the same command as Step 3. Expected: `exit=0`, `Failures: 0`,
`Errors: 0`.

- [ ] **Step 6: Commit**

```sh
cd /Users/darkbit1001/workspace/demo-chat
git add chat-security/src/main/kotlin/com/demo/chat/security/service/CoreAuthorizationService.kt \
        chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt
git commit -m "A permission check reads the target and its domain root (CHAT-rfzsnbco)

getAuthorizationsAgainst read one target, so a grant on a domain root
never reached an object of that domain. It reads the given target and the
domain root of that target now.

One matrix row moves. messageById reads allow, because {Anon, Message,
GET} names the Message root and a message read asks about one message.

send stays denied. The Message root row does not cover a room.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: The counting index proves which targets a check reads

A count proves nothing about which key was read. A count of one also holds for
two reads of one key, or for a read of the wrong key. **The assertion reads the
recorded target keys.**

This task also proves the exact-target owner rule, which the whole design rests
on.

**Files:**
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`

**Interfaces:**
- Consumes: `targets` from Task 2.
- Produces: a `RecordingAuthIndex` class, a `RecordingAuth` holder and a
  `recordingAuth(grants)` helper. Task 4 and Task 5 use them.

- [ ] **Step 1: Make the map index open, and add the recording index**

Replace the class declaration of `MapAuthIndex`, at line 407.

```kotlin
    /** The authorization index, which answers by target key. */
    private open class MapAuthIndex(private val store: MapAuthStore) :
        IndexService<Long, AuthMetadata<Long>, Key<Long>> {

        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.fromIterable(store.rows.values.filter { it.target == query }.map { it.key })

        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = findBy(query).next()
    }

    /** An index that records every target it is asked for, in order. */
    private class RecordingAuthIndex(store: MapAuthStore) : MapAuthIndex(store) {
        val asked: MutableList<Key<Long>> = mutableListOf()

        override fun findBy(query: Key<Long>): Flux<out Key<Long>> {
            asked.add(query)
            return super.findBy(query)
        }
    }

    /** The broker, the service and the recording index of one grant set. */
    private class RecordingAuth(
        val broker: AuthMetadataAccessBroker<Long>,
        val service: CoreAuthorizationService<Long, Key<Long>>,
        val index: RecordingAuthIndex,
    )
```

- [ ] **Step 2: Add the recording helper**

Add this helper beside `broker`, at line 348.

```kotlin
    /** The same stack as [broker], with an index that records each target. */
    private fun recordingAuth(grants: List<AuthMetadata<Long>>): RecordingAuth {
        val store = MapAuthStore()
        val index = RecordingAuthIndex(store)
        grants.forEach { store.rows[it.key] = it }
        val service = CoreAuthorizationService(
            store, index, { it }, { it }, { ANON_KEY }, { USER_ROOT },
            AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys())),
            registry(),
        )
        return RecordingAuth(
            AuthMetadataAccessBroker(service, TestVerifiers.resolvingNothing()), service, index
        )
    }
```

- [ ] **Step 3: Write the failing tests**

Add these six tests beside the tests of Task 2.

```kotlin
    /**
     * **A check that already names a domain root reads one target.** A root key
     * is its own root, so the scan adds nothing. The index records the key it
     * was asked for, and not a count.
     */
    @Test
    fun `a domain root check reads one target`() {
        val auth = recordingAuth(shippedGrants())

        auth.broker.hasAccessByKey(CALLER_KEY, TOPIC_ROOT.verified(), "ALL").block()

        assertThat(auth.index.asked).containsExactly(TOPIC_ROOT)
    }

    /**
     * **A check on one object reads the object and its domain root.** The room
     * key carries the `MessageTopic` root, so the check reads both.
     */
    @Test
    fun `an object check reads the object and its domain root`() {
        val auth = recordingAuth(shippedGrants())

        auth.broker.hasAccessByKey(CALLER_KEY, ROOM_KEY.verified(), "GET").block()

        assertThat(auth.index.asked).containsExactly(ROOM_KEY, TOPIC_ROOT)
    }

    /**
     * **Owner selection reads the exact target.** A wildcard row on the domain
     * root must not enter the selection for one room. A domain root read there
     * would give one target two owners, which is the rule the owner set on
     * 2026-09-24.
     */
    @Test
    fun `owner selection reads the exact target alone`() {
        val named = grant(USER_ROOT, ROOM_KEY, "GET")
        val ownerRow = grant(USER_ROOT, TOPIC_ROOT, "*")
        val auth = recordingAuth(listOf(named, ownerRow))

        val selected = auth.service.getAuthorizationsForTarget(ROOM_KEY).collectList().block()!!

        assertThat(auth.index.asked).describedAs("the targets the selection read").containsExactly(ROOM_KEY)
        assertThat(selected.map { it.target }).describedAs("the targets it selected").containsExactly(ROOM_KEY)
    }

    /**
     * **Self authority answers before any read.** The rule is in the broker, so
     * a check of a key against itself must leave the index unread.
     */
    @Test
    fun `a check of a key against itself reads no target`() {
        val auth = recordingAuth(shippedGrants())

        val answer = auth.broker.hasAccessByKey(CALLER_KEY, CALLER_KEY.verified(), "GET").block()

        assertThat(answer).isTrue()
        assertThat(auth.index.asked).isEmpty()
    }

    /**
     * **A check with no permission still reads both targets.** A null
     * permission lists the rows as they are stored, and the scan is not part of
     * that decision.
     */
    @Test
    fun `a check with no permission reads both targets`() {
        val auth = recordingAuth(shippedGrants())

        auth.service.getAuthorizationsAgainst(CALLER_KEY, ROOM_KEY, null).collectList().block()

        assertThat(auth.index.asked).containsExactly(ROOM_KEY, TOPIC_ROOT)
    }

    /**
     * **A root that no domain holds is read as a target too.** The scan trusts
     * the root of the key, which the boundary verified. A root outside the
     * registry matches no row, and it does not throw.
     */
    @Test
    fun `a target with an unregistered root reads it and matches nothing`() {
        val alien = Key.of(9L, 404L)
        val auth = recordingAuth(shippedGrants())

        val rows = auth.service.getAuthorizationsAgainst(CALLER_KEY, alien, "GET").collectList().block()!!

        assertThat(auth.index.asked).containsExactly(alien, Key.root(404L))
        assertThat(rows).isEmpty()
    }
```

- [ ] **Step 4: Run the tests and confirm they pass**

The scan of Task 2 supplies the behaviour, so this task adds no implementation
step. **These tests are a characterization of code that already landed, and a
test that has only ever passed proves nothing.** The next step is the proof.

Run the Step 3 command of Task 2 with `/tmp/t3.log`.

Expected: `exit=0`, `Failures: 0`, `Errors: 0`.

- [ ] **Step 5: Prove the tests by mutation, then restore**

**Do not use `git checkout -- <file>`.** It discards any edit that is in the
file when it runs, including an edit that another tool made. This procedure
records the mutation and reverses exactly that recording.

First, assert that the file is clean, and record its hash. **Stop if the
assertion prints anything.** An unclean file means another edit is present, and
this task must not touch it.

```sh
cd /Users/darkbit1001/workspace/demo-chat
SERVICE=chat-security/src/main/kotlin/com/demo/chat/security/service/CoreAuthorizationService.kt
git status --short -- "$SERVICE"
shasum -a 256 "$SERVICE"
```

Expected: the `git status` line prints nothing. Record the hash.

Next, mutate the file by hand. In `getAuthorizationsAgainst`, replace
`targets(uidB)` with `listOf(uidB)`. Save the mutation diff, and run the Step 3
command of Task 2 again.

```sh
cd /Users/darkbit1001/workspace/demo-chat
SERVICE=chat-security/src/main/kotlin/com/demo/chat/security/service/CoreAuthorizationService.kt
git diff -- "$SERVICE" > /tmp/t3-mutation.diff
wc -l /tmp/t3-mutation.diff
```

Expected: the diff holds two changed lines, one removed and one added.

Expected on the test run: FAIL on `a domain root check reads one target`,
`an object check reads the object and its domain root`,
`a check with no permission reads both targets` and
`a target with an unregistered root reads it and matches nothing`. The owner
selection test must still PASS, because that read did not change.

Reverse the recorded mutation, and prove the restore.

```sh
cd /Users/darkbit1001/workspace/demo-chat
SERVICE=chat-security/src/main/kotlin/com/demo/chat/security/service/CoreAuthorizationService.kt
git apply -R /tmp/t3-mutation.diff
git status --short -- "$SERVICE"
shasum -a 256 "$SERVICE"
```

Expected: `git status --short` prints nothing, which means the file matches
HEAD. The hash equals the hash recorded before the mutation.

**Stop and report if either check differs.** Do not run `git checkout --`, and
do not edit the file to make the hash match.

- [ ] **Step 6: Run once more, and commit**

Run the Step 3 command of Task 2 with `/tmp/t3.log`. Expected: `exit=0`,
`Failures: 0`, `Errors: 0`.

```sh
cd /Users/darkbit1001/workspace/demo-chat
git add chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt
git commit -m "Prove the scanned targets by key, and hold the owner selection exact (CHAT-rfzsnbco)

A count proves nothing about which key was read. The index records every
target it is asked for, and the assertions read the recorded keys.

The owner selection is read the same way, and it must read the exact
target alone. A domain root read there would give one target two owners.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: The many-target path takes the same expansion

`getAuthorizationsAgainstMany` has no production caller today. It asks the same
question as the single path, so the two must agree.

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/service/CoreAuthorizationService.kt:129-134`
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`

**Interfaces:**
- Consumes: `targets` from Task 2, `recordingAuth` from Task 3.
- Produces: nothing that a later task reads.

- [ ] **Step 1: Write the failing tests**

Add these three tests beside the tests of Task 3.

```kotlin
    /**
     * **A many target request mixes one object and one domain root.** The
     * request holds a room and the `MessageTopic` root. Each entry is expanded
     * on its own, so the room reads two targets and the root reads one.
     */
    @Test
    fun `a mixed many target request expands each entry alone`() {
        val auth = recordingAuth(listOf(grant(CALLER_KEY, TOPIC_ROOT, "GET")))

        auth.service.getAuthorizationsAgainstMany(CALLER_KEY, listOf(ROOM_KEY, TOPIC_ROOT), "GET")
            .collectList().block()!!

        assertThat(auth.index.asked).containsExactly(ROOM_KEY, TOPIC_ROOT, TOPIC_ROOT)
    }

    /** The same request, through the broker, permits both targets. */
    @Test
    fun `a mixed many target request permits the object and the root`() {
        val broker = broker(listOf(grant(CALLER_KEY, TOPIC_ROOT, "GET")))

        assertThat(permitted(broker, listOf(ROOM_KEY, TOPIC_ROOT), "GET"))
            .containsExactly(ROOM_KEY, TOPIC_ROOT)
    }

    /**
     * **A repeated target is read once per occurrence.** The many path expands
     * each entry alone, and it de-duplicates nothing across entries.
     */
    @Test
    fun `a repeated target is read once per occurrence`() {
        val auth = recordingAuth(shippedGrants())

        auth.service.getAuthorizationsAgainstMany(CALLER_KEY, listOf(ROOM_KEY, ROOM_KEY), "GET")
            .collectList().block()!!

        assertThat(auth.index.asked).containsExactly(ROOM_KEY, TOPIC_ROOT, ROOM_KEY, TOPIC_ROOT)
    }

    /**
     * **An empty many target list reads no target.** It answers nothing, and it
     * does not read the store.
     */
    @Test
    fun `an empty many target list reads no target`() {
        val auth = recordingAuth(shippedGrants())

        val rows = auth.service.getAuthorizationsAgainstMany(CALLER_KEY, listOf(), "GET")
            .collectList().block()!!

        assertThat(auth.index.asked).isEmpty()
        assertThat(rows).isEmpty()
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run the Step 3 command of Task 2 with `/tmp/t4.log`.

Expected: FAIL on `a mixed many target request expands each entry alone` and on
`a repeated target is read once per occurrence`. Both record one target per
entry, so the room contributes one entry instead of two.

- [ ] **Step 3: Write the minimal implementation**

Replace the body of `getAuthorizationsAgainstMany`, at lines 129 to 134.

```kotlin
    override fun getAuthorizationsAgainstMany(uidA: Key<T>, uidB: List<Key<T>>, permission: String?): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            Flux.concat(uidB.flatMap { targets(it) }.map { authIndex.findBy(queryForTarget.apply(it)).flatMap(authPersist::get) }),
            actors(uidA),
            permission
        )
```

**If the compiler cannot infer the element type of `Flux.concat`, name it.**
Write `Flux.concat<AuthMetadata<T>>(...)`. Do not restructure the chain.

- [ ] **Step 4: Run the tests and confirm they pass**

Run the same command. Expected: `exit=0`, `Failures: 0`, `Errors: 0`.

- [ ] **Step 5: Commit**

```sh
cd /Users/darkbit1001/workspace/demo-chat
git add chat-security/src/main/kotlin/com/demo/chat/security/service/CoreAuthorizationService.kt \
        chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt
git commit -m "The many target read expands each entry the same way (CHAT-rfzsnbco)

getAuthorizationsAgainstMany asks the same question as the single target
path, so the two must agree. Each entry now contributes its own domain
root.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: The administrator acts on a closed target

The owner named this invariant on 2026-09-24 as the reason an expired wildcard
is safe. A row `{Admin, <Domain>, '*', never}` must beat a close on one object
of that domain.

**The caller must hold the `Admin` key.** The actor set is the `Anon` key, the
`User` root and the caller. The `Admin` key is neither the `User` root nor the
`Anon` key, so an anonymous caller must fail this test.

**Files:**
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`

**Interfaces:**
- Consumes: `recordingAuth`, `broker`, `rootKeys`, `registry`, `grant`.
- Produces: `adminContext()` and `adminDetails()`. Only this task reads them.

- [ ] **Step 1: Write the failing test**

Add this test beside the tests of Task 4.

```kotlin
    /**
     * **An administrator acts on a closed target.**
     *
     * The close is an expired wildcard row on the `User` root, which is a
     * domain root principal. The administrator row is a live wildcard on the
     * `Admin` key, which is an object principal. Level 1 places both at the
     * wildcard, and level 2 places `ENTITY` above `DOMAIN_ROOT`. So the
     * administrator row is last and it decides.
     *
     * **The context must carry the `Admin` key.** The actor set is the `Anon`
     * key, the `User` root and the caller. An anonymous caller does not hold
     * the `Admin` key, so it must fail.
     */
    @Test
    fun `an administrator acts on a closed target`() {
        val rows = listOf(
            grant(ADMIN_KEY, TOPIC_ROOT, "*"),
            grant(USER_ROOT, ROOM_KEY, "*", expires = 1L)
        )
        val service = SpringSecurityAccessBrokerService(broker(rows), rootKeys(), registry())

        val admin = service.hasAccessTo(ROOM_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(adminContext())))
            .block()
        val anonymous = service.hasAccessTo(ROOM_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(anonymousContext())))
            .block()

        assertThat(admin).describedAs("the administrator").isTrue()
        assertThat(anonymous).describedAs("an anonymous caller").isFalse()
    }

    /**
     * **A close still beats a domain root grant.** Both rows name the
     * `MessageTopic` root as their target. The close is a wildcard, so level 1
     * places it last, and its expiry decides.
     */
    @Test
    fun `a close beats a live named row on a domain root`() {
        val rows = listOf(
            grant(USER_ROOT, TOPIC_ROOT, "GET"),
            grant(USER_ROOT, TOPIC_ROOT, "*", expires = 1L)
        )
        val service = SpringSecurityAccessBrokerService(broker(rows), rootKeys(), registry())

        val answer = service.hasAccessTo(ROOM_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).describedAs("a room read after a close").isFalse()
    }
```

- [ ] **Step 2: Add the administrator context**

Add these two helpers beside `authenticatedContext`, at line 372.

```kotlin
    /** A context whose caller is the `Admin` key. */
    private fun adminContext() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(adminDetails(), "secret", listOf())
    )

    private fun adminDetails() =
        ChatUserDetails(User.create(ADMIN_KEY, "a", "admin", "http://a"), listOf())
```

- [ ] **Step 3: Run the tests**

Run:

```sh
cd /Users/darkbit1001/workspace/demo-chat
mvn -o -B -pl chat-core,chat-security test \
  -Dtest=AnonymousAuthorizationMatrixTests \
  -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t5.log 2>&1
echo "exit=$?"; grep -E "Tests run:|BUILD" /tmp/t5.log | tail -5
```

Expected: PASS, because the scan of Task 2 supplies the behaviour. `Failures: 0`
and `Errors: 0`.

**If `an administrator acts on a closed target` fails on the administrator
line, do not weaken the test.** Report the failure. The rank rule is the
subject of a separate decision, and this plan does not change it.

- [ ] **Step 4: Commit**

```sh
cd /Users/darkbit1001/workspace/demo-chat
git add chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt
git commit -m "Prove the administrator invariant on a closed target (CHAT-rfzsnbco)

The administrator row names the Admin key, which is an object principal,
so level 2 of the rank places it above a close on a domain root. The
context carries the Admin key, and a second case proves an anonymous
caller does not satisfy the row.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: The Cassandra matrix takes the same reading

`CassandraAuthorizationMatrixTests` runs the production stack against a real
Cassandra store and a real Cassandra index. Its rows are the map-store rows, and
`messageById` moved.

**Files:**
- Modify: `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraAuthorizationMatrixTests.kt`

**Interfaces:**
- Consumes: nothing from the earlier tasks. The fixture there is production keys,
  which already carry their domain root.
- Produces: nothing that a later task reads.

- [ ] **Step 1: Add the room read to the matrix**

In `matrix`, at lines 175 to 182, add one entry after the `send room SEND` line.

```kotlin
        "getRoom GET" to answer({ it.hasAccessTo(room, "GET") }, access, context),
```

- [ ] **Step 2: Move the `messageById` row and add the room row to the expectation**

Replace the `expected` map, at lines 136 to 143.

```kotlin
            val expected = linkedMapOf(
                "addRoom MessageTopic NEW" to false,
                "send room SEND" to false,
                "getRoom GET" to true,
                "whoami User FIND" to true,
                "messageById GET" to true,
                "listRooms MessageTopic ALL" to true,
                "addUser User NEW" to false
            )
```

- [ ] **Step 3: Correct the class comment of the test**

Replace the paragraph at lines 92 to 103.

```kotlin
    /**
     * **The operations answer the same on Cassandra as on a map store.**
     *
     * `getRoom`, `whoami`, `messageById` and `listRooms` allow. `getRoom` and
     * `messageById` read one object, and since `CHAT-rfzsnbco` the check reads
     * the domain root of that object beside it. `userinit.yml` names both roots:
     * `{User, MessageTopic, GET}` and `{Anon, Message, GET}`. `whoami` and
     * `listRooms` name a domain root already.
     *
     * `addRoom`, `send` and `addUser` deny. No shipped row grants `NEW` on
     * `MessageTopic` or on `User`, and the `{User, Message, SEND}` row names
     * the `Message` root, which is not the root of a room.
     */
```

Also correct the sentence at lines 99 to 102 of the old text is inside the
replacement above. Nothing else in that comment moves.

- [ ] **Step 4: Run the test**

This test is a Boot test in a deploy module. **A scoped `-pl` run resolves
upstream modules from `~/.m2`, and a stale jar there reports a missing bean.**
Run the full-reactor integration mode instead.

```sh
cd /Users/darkbit1001/workspace/demo-chat
./shell-scripts/build-health.sh --ci > /tmp/t6.log 2>&1
echo "exit=$?"; tail -30 /tmp/t6.log
```

Expected: `exit=0`, and the verifier reports that reality matches
`docs/BUILD-HEALTH.md`.

The run takes about 20 minutes. Start it and wait.

- [ ] **Step 5: Commit**

```sh
cd /Users/darkbit1001/workspace/demo-chat
git add chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraAuthorizationMatrixTests.kt
git commit -m "The Cassandra matrix takes the target domain scan reading (CHAT-rfzsnbco)

messageById moves to allow, because {Anon, Message, GET} names the
Message root and a message read asks about one message. getRoom is a new
row, and it allows through {User, MessageTopic, GET}.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: The documents take the measured result

**Files:**
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`
- Modify: `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`
- Modify: `forward-register.md`

**Interfaces:**
- Consumes: the measured results of Tasks 2 to 6.
- Produces: nothing that a later task reads.

- [ ] **Step 1: Move the matrix row**

In `docs/ANONYMOUS-AUTHORIZATION.md`, replace the matrix at lines 129 to 136.

```markdown
| Operation | anonymous | authenticated | unauthenticated | unsupported | no context |
|---|---|---|---|---|---|
| `addRoom`, MessageTopic NEW | deny | deny | deny | deny | deny |
| `send`, room SEND | deny | deny | deny | deny | deny |
| `whoami`, User FIND | **allow** | **allow** | deny | deny | deny |
| `messageById`, GET | **allow** | **allow** | deny | deny | deny |
| `listRooms`, MessageTopic ALL | **allow** | **allow** | deny | deny | deny |
| `addUser`, User NEW | deny | deny | deny | deny | deny |
```

- [ ] **Step 2: Correct the grants table**

Replace the table at lines 64 to 78.

```markdown
| Principal | Target | Permission | Reaches a caller |
|---|---|---|---|
| `Admin` | `Admin` | `*` | the `Admin` key alone, and the self rule already covers it |
| `Anon` | `User` | FIND | yes |
| `Anon` | `User` | PUT | yes |
| `Anon` | `Message` | GET | yes, and it reaches `messageById` since `CHAT-rfzsnbco` |
| `User` | `Message` | SEND | yes, and it reaches no room, because a room is another domain |
| `User` | `MessageTopic` | ALL | yes |
| `User` | `MessageTopic` | GET | yes, and it reaches `getRoom` and `getRoomByName` since `CHAT-rfzsnbco` |
| `User` | `MessageTopic` | JOIN | yes, and it reaches `leaveRoom` since `CHAT-rfzsnbco` |
| `User` | `MessageTopic` | MEMBERS | yes, and it reaches `roomMembers` since `CHAT-rfzsnbco` |
```

Replace the paragraph at lines 76 to 78.

```markdown
"Reaches a caller" is the principal side, which `CHAT-mahevldm` closed.
"Reaches an operation" is the target side, which `CHAT-rfzsnbco` closed. A
check on one object now reads the domain root of that object beside it.
```

- [ ] **Step 3: Replace result 3**

Replace the whole of result 3, at lines 159 to 172.

```markdown
3. **A grant on a domain root covers an object of that domain, since
   `CHAT-rfzsnbco`.** A permission check reads the given target and the domain
   root of that target. The owner selection does not, because a domain root
   read there would give one target two owners.

   `Anon` holds `Message:GET`, and `messageById` checks one message key. The
   check now reads the `Message` root beside that key, so the row applies.
   `send` stays denied, because the `Message` root is not the root of a room.

   **Five checks name a root as the target already.** `hasAccessToDomain`
   passes the domain root, so `addRoom`, `listRooms`, `addUser`,
   `findByUsername` and `findByUserId` compare against it directly. At those
   five the scan adds nothing, because a root key is its own root.

   **`User:MessageTopic:GET`, `JOIN` and `MEMBERS` now reach an operation
   each.** Those three checks name one room, and the scan reads the
   `MessageTopic` root of that room. See the wider-effect table of
   `docs/superpowers/specs/2026-09-30-target-domain-scan-design.md`.
```

- [ ] **Step 4: Correct the paragraph after result 3**

Replace the paragraph at lines 174 to 179.

```markdown
So the shipped configuration allows `User:FIND`, `User:PUT`,
`MessageTopic:ALL`, `Message:GET` and three named `MessageTopic` permissions
to every caller that reaches an identity. **The six matrix write operations
still deny for every caller.**

`addRoom` and `addUser` deny although both sides match, because no shipped row
grants `NEW` on either domain. `send` denies, and `deleteRoom` denies.

**Three checks outside the six now allow.** `PersistenceAccess.add` and
`IndexAccess.add` reach `{Anon, User, PUT}` for a `User` entity, and
`PersistenceAccess.get` and `byIds` reach `{Anon, Message, GET}` for a
`Message` entity. Every one of them is latent. See the wider-effect table of
the spec.
```

- [ ] **Step 5: Correct "What is not wired"**

Replace the first paragraph, at lines 190 to 195.

```markdown
**No production type implements the annotated interfaces.**
`TopicServiceAccess`, `UserServiceAccess` and `MessageServiceAccess` in
`com.demo.chat.security.access.composite` carry the checks that name a domain
as text. The `core` package carries more, over `PersistenceAccess`,
`IndexAccess`, `PubSubAccess` and `IKeyServiceAccess`. Every one of them is
latent. `CompositeControllersConfiguration` imports the three composite
interfaces and implements none of them. The controllers delegate to
`CompositeServiceBeans`, which supplies the plain services.
```

- [ ] **Step 6: Correct the draft**

In `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`, replace the
paragraph at lines 263 to 266.

```markdown
A permission scan must query **the given target and the domain root of that
target**. `CoreAuthorizationService` performs one lookup at
`getAuthorizationsAgainst`, which is the permission check path. It reads both
targets since `CHAT-rfzsnbco`.

**Two of the four reads stay exact.** `getAuthorizationsForTarget` and
`getAuthorizationsForMultipleTarget` select the owner of a target. The owner
decided on 2026-09-24 that one caller holds `*` per target. A domain root read
there would answer two holders of `*` for one room, so the selection reads the
exact target alone.
```

- [ ] **Step 7: Record the work in the register**

Add this section to `forward-register.md`, after the last section.

```markdown
## The target domain scan (2026-09-30)

`CHAT-rfzsnbco`. Spec:
`docs/superpowers/specs/2026-09-30-target-domain-scan-design.md`. Plan:
`docs/superpowers/plans/2026-09-30-target-domain-scan.md`.

A permission check reads the given target and the domain root of that target.
The owner selection reads the exact target alone.

Two facts that are expensive to relearn:

1. **The matrix fixture carried no domain root.** `TestKeys.key(id)` gives
   every key the fixed root `-9L`. So no test key carried the root of its own
   domain, and the scan could not be measured. A room key must carry the
   `MessageTopic` root. The fixture builds a root as `Key.root(id)`, an object
   as `Key.of(id, domainRootId)`, and an identity as `Key.of(id, userRootId)`.
2. **One matrix row moves.** `messageById` reads allow, because
   `{Anon, Message, GET}` names the `Message` root and a message read asks
   about one message. `send` stays denied, because the `Message` root is not
   the root of a room.

Every moved check is latent. No production bean implements an annotated
interface, so this change moves what the configuration means and no running
answer.
```

- [ ] **Step 8: Run the drift check and the diff check**

```sh
cd /Users/darkbit1001/workspace/demo-chat
drift check; echo "drift=$?"
git diff --check; echo "diffcheck=$?"
```

Expected: `drift=0` and `diffcheck=0`.

- [ ] **Step 9: Commit**

```sh
cd /Users/darkbit1001/workspace/demo-chat
git add docs/ANONYMOUS-AUTHORIZATION.md \
        docs/superpowers/specs/2026-09-23-operation-policy-draft.md \
        forward-register.md
git commit -m "Record the target domain scan in the documents (CHAT-rfzsnbco)

The matrix moves one row. messageById reads allow. The grants table
states which operations each shipped row now reaches, and the draft
records that two of the four reads stay exact.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## The final gate

- [ ] **Step 1: Run the default build**

```sh
cd /Users/darkbit1001/workspace/demo-chat
./shell-scripts/build-health.sh > /tmp/final-default.log 2>&1
echo "exit=$?"; tail -30 /tmp/final-default.log
```

Expected: `exit=0`.

- [ ] **Step 2: Run the integration build**

```sh
cd /Users/darkbit1001/workspace/demo-chat
./shell-scripts/build-health.sh --ci > /tmp/final-ci.log 2>&1
echo "exit=$?"; tail -30 /tmp/final-ci.log
```

Expected: `exit=0`. This run builds the test image and runs the container
tests.

- [ ] **Step 3: Comment on the issue and push**

```sh
cd /Users/darkbit1001/workspace/demo-chat
fp comment CHAT-rfzsnbco "Implementation complete. The permission check reads the given target and its domain root. The owner selection stays exact. One matrix row moves: messageById reads allow. Default and --ci gates exit 0."
git push -u origin chat-rfzsnbco-target-domain-scan
```

- [ ] **Step 4: Open the pull request**

```sh
cd /Users/darkbit1001/workspace/demo-chat
gh pr create --title "The target domain scan (CHAT-rfzsnbco)" --body "$(cat <<'EOF'
A permission check reads the given target and the domain root of that target.
The owner selection reads the exact target alone.

- `CoreAuthorizationService.targets` builds the list, and a root key is its own
  root, so a check that already names a domain root reads one target.
- `getAuthorizationsAgainst` and `getAuthorizationsAgainstMany` use it.
- `getAuthorizationsForTarget` and `getAuthorizationsForMultipleTarget` do not.
  A domain root read there would give one target two owners.

One row of the measured matrix moves. `messageById` reads allow, because
`{Anon, Message, GET}` names the `Message` root. `send` stays denied, because
the `Message` root is not the root of a room.

Every moved check is latent. No production bean implements an annotated
interface.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

## The working tree

**This work does not touch the unrelated changes in the working tree.**
`userinit.yml` and `CompositeControllersConfiguration.kt` carry uncommitted
edits that belong to another line of work. Stage named paths only.

The committed `userinit.yml` holds nine roles. The working tree holds eight,
because it removes `{ user: Anon, target: User, role: PUT }`. **The mirror in
`AnonymousAuthorizationMatrixTests.shippedGrants()` stays as it is.** Record
the difference on the issue in the final gate. Do not resolve it here.
