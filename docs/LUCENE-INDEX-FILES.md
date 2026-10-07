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

## Write cost

Each index write commits, and a commit in files mode syncs to disk. So files
mode runs each write on a scheduler that permits blocking, not on the thread of
the caller. A disk sync on a Netty event loop would delay every connection on
that loop. Memory mode keeps the caller thread. Measured
on 2026-10-07 on a macOS development machine, one Redis `addUser` took about
25 ms with file indexes and about 2 ms with memory indexes. One isolated index
add took about 28 ms with files. So file mode allows about 35 commits per
second for one index on that machine. Linux was not measured. Batched commits
are a separate decision.

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
