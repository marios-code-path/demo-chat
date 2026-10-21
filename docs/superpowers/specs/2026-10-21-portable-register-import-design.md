# Portable Forward Register Import

Date: 2026-10-21. Status: proposed. Issue: `CHAT-jmnjvupr`.

## Goal and scope

One bundle restores an active topic register from one reviewed Markdown snapshot on any prepared node-9 host.
The bundle holds the source bytes, an explicit topic mapping, and stable request IDs.
It holds no password or host-specific receipt.

This is the bootstrap delivery before stage 4 removes the tracked register files.
The later archive import can use the same transport and byte checks.
This delivery does not import the six historical archive files.

The import restores content. It does not restore old message IDs, senders, or timestamps.
A Cassandra backup is required to preserve that history.

## Inputs and bundle

The source is a reviewed UTF-8 `forward-register.md` snapshot.
The caller records its source date and evidence before `pack` runs.
Commit `2d75f0cdd09115da422cdc0a693f0227f31a6ef5` (H) is historical input only.
It predates later stage 3 register entries and cannot be the current active source.
The current host needs a new snapshot from the stage 3 proof and later FP records.
Mario reviews that snapshot before the active import.
The `pack` command accepts a file, so a destination host needs no Git checkout.

The caller supplies `mapping.json` with this shape:

```json
{
  "version": 1,
  "source_sha256": "64 lowercase hexadecimal characters",
  "source_date": "2026-10-21",
  "source_basis": "Owner-reviewed snapshot from the stage 3 proof and FP records",
  "session": {"first_line": 23, "after_line": 35},
  "entries": [
    {
      "key": "CHAT-jmnjvupr",
      "topic": "forward-register-topic-register",
      "title": "Topic register",
      "first_line": 36,
      "after_line": 37
    }
  ]
}
```

Line numbers start at one. `first_line` is inclusive, and `after_line` is exclusive.
The example numbers describe the format. They are not line numbers from a source file.
The session range selects the text that every session read must show.
Each entry range selects one active message for its key.
The caller reviews the mapping before `pack` creates the bundle.
The source date and basis describe the reviewed snapshot, not a claim made by `pack`.

`pack` requires the source hash to match `source_sha256`.
It converts each line range to exact byte offsets without changing line endings.
It rejects a range outside the source or a range that starts after its end.
It rejects an empty range, a repeated key, a repeated topic, and an unsafe topic name.
It applies the existing generic key rule. It does not require a `CHAT-` prefix.
It rejects a session or entry range above 16,384 bytes.
It does not infer a key or topic from a Markdown table.

`pack` copies the exact source into a new private bundle directory.
It splits the source at `## ` headings, with the preamble as part zero.
It refuses a source without a preamble, invalid UTF-8, or a NUL byte.
It refuses a section above 16,384 bytes.
It records each part's byte count, SHA-256, and order.
It records the mapping and the source SHA-256 in a canonical content manifest.
It records the computed byte offsets and hashes for every mapped range.
The canonical form is `jq -cS` output with one final LF under the C locale.

The importer hashes the content manifest before it assigns request IDs.
It derives each request ID from a fixed UUIDv5 namespace and that content hash.
The UUIDv5 namespace is `e9d6d78a-0020-43e3-9893-92737b9e536c`.
The name also contains the message kind, topic, and part index.
The same source and mapping produce the same request IDs on every host.
A changed source or mapping produces a different bundle identity.
The final bundle manifest records the content hash and the derived request IDs.

The bundle directory uses mode `0700`. Its files use mode `0600`.
The bundle is task data and may contain private project text.

## Destination workflow

`node-9/bin/register-import pack --source FILE --mapping FILE --bundle DIR` makes no network call.
It refuses an existing bundle directory.
It prints the source hash, bundle hash, topic names, and byte counts for review.

The owner copies the bundle to the destination host.
The destination stores receipts under `node-9/data/register-import/<bundle-hash>`.
It does not copy receipts from another host.

`node-9/bin/register-import apply --bundle DIR --as Mario` imports one bundle.
It accepts no other import user for the first delivery.
It checks the bundle before any shell call.
It checks the installed shell CLI, all four credentials, and health on port `6891`.
It fixes core ports `6890` and `6891`, node ID `9`, and UUID keys.
It refuses a caller override of those values.
It sends no password in an argument or the bundle.

The first import runs on a fresh host before agents use its register.
The first live step checks that `register-index` and every target topic are absent.
Mario then creates `register-index`.
The importer saves the new index ID and Mario ownership proof in private host state.
The proof checks the Cassandra `*` permission row and resolves its principal to `Mario`.
A retry accepts an existing index only when that saved proof matches the live index.
An existing index without that proof stops the import before another write.
A denied or failed read stops the import.
The importer checks every target name before its first write.
An existing name with unrelated content stops the import.

The importer creates one snapshot topic named from the source hash.
It posts a provenance header, then each source part in order.
It posts the exact session range last.
The last message makes the session range visible through `register-read`.
The importer reads the topic and verifies every posted message ID and text.
It verifies that the session range is the newest message.
It joins Sigma, Theta, Phi, and Mario to the snapshot topic.

For each mapped entry, the importer creates its named topic.
It posts a provenance header, then the exact source range.
It reads the topic and verifies the entry ID and text.
It verifies that the entry is the newest message.
It joins Sigma, Theta, Phi, and Mario before it posts the key pointer.
The pointer has four fields: key, topic, metadata, and title.
The metadata names the bundle hash and has no interpretation in `register-read`.
After every topic passes, the importer posts the snapshot pointer and each key pointer.
It reads the index and verifies each new pointer ID.
Agents cannot use the new host until `verify` passes.

The importer records a receipt after each successful post.
A retry uses the same request ID and the same input bytes.
If a post outcome is uncertain, it retries that request ID.
The importer refuses a changed receipt ID for the same request ID.
It never treats an existing unrelated topic or pointer as a completed import.
It does not overwrite a newer entry in a topic that already contains this bundle.

`node-9/bin/register-import verify --bundle DIR --as Mario` reads the destination.
It reconstructs the source from the ordered snapshot parts.
It compares the result with the bundled source by `cmp` and SHA-256.
It verifies each mapped entry against its source range.
It verifies the pointers and reads the register as all four users.
It treats a denied warning as a failure, even when the read returns status `0`.
It measures five complete session reads from command start to the last byte.
The median must meet the existing 10-second target.
A read at the 30-second limit must return status `124`.

## Failure rules

An invalid bundle or missing credential stops before a live write.
A failed topic check stops before its pointer post.
A failed byte comparison leaves the source file in the repository.
A failed time gate leaves the source file in the repository.
The importer never removes a source file itself.

The receipt directory is private and belongs to one host.
The bundle contains no receipt, password, or destination message ID.
The importer prints one error line without secret text and returns a nonzero status.

## Boundaries and acceptance

The first delivery imports one current Markdown file and its active mapping.
The frozen file at H remains available for the separate historical import.
The existing stage 4 archive plan follows this delivery.
It must use the bundle transport before it removes the seven tracked files.
The paused `import-register` prototype is tied to H and seven fixed files.
Its uncommitted apply path has not passed its offline suite.

Offline tests use a shell stub and two separate destination states.
They prove equal bundle IDs, equal request IDs, byte recovery, and safe retries.
They cover missing credentials, a denied index, a topic conflict, and a malformed receipt.
They prove that a pointer never precedes the readback of its topic.

Live acceptance uses the installed node-9 home.
It requires a working installed CLI and a rendered Mario password first.
The current installed `register read` returns status `2` because the shell CLI is absent.
The installed home currently has no Cassandra data.
Those facts block live acceptance. They do not block offline work.

The final proof records hashes, message IDs, counts, statuses, and times.
It does not copy passwords or register text into the proof.
The owner reviews the proof before the stage 4 file removal.
