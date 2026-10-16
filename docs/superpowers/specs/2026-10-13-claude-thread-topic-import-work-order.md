# Claude thread topic import work order

Issue: `CHAT-emwhnlay`.

Status: Reviewed by Theta on 2026-10-14. Implementation in progress.

## Goal

Import every thread in one selected Claude conversation. Create one Demo Chat topic for each thread. Do not create `PointerMessage` records.

## Import rules

1. Read each message record's `parent_message_uuid` to find its parent.
2. Treat each leaf message as the end of one thread.
3. Build each thread from its root message to its leaf message.
4. Include shared parent records in every thread that uses them.
5. Import each thread's records in parent-to-child order into its topic.
6. Stop a thread at its first parent UUID that is absent from the export. Report that boundary.
7. Keep the current text extraction and skip rules.
8. Validate both user handles before creating topics or importing messages.
9. Derive each message key from its topic ID and Claude message UUID, as `CHAT-zxrgxdlx` specifies.
10. Keep imports serial and safe to repeat.

## Topic naming

Name every topic `<base>:<conversation-uuid>:<generation>:<leaf-uuid>`. Use canonical UUID text, with hyphens. Include the leaf UUID when the conversation has one path. Do not assign sequence numbers. An additional leaf must not rename an existing topic.

The separator is `:`. The operator supplies the generation as a decimal value. The importer must not compute it.

**The hyphen ban is removed on 2026-10-14.** It rested on the Lucene name lookup, which split a name on a non-alphanumeric character. PR #224 made the topic name a whole-value match, so a colon and a hyphen are both safe in a name. See `CHAT-hajmhslp`.

The current command still refuses a hyphenated `--topic` base, at `ClaudeImportCommands.kt:31`. That guard stays until this work changes it.

## Message identity

Follow `CHAT-zxrgxdlx`: derive the message key with `UUID.nameUUIDFromBytes("<topicId>:<messageUuid>".encodeToByteArray())`. The topic ID makes a copied ancestor distinct in each topic. Keep the existing `import:` request ID and `MessageImportRequest.messageId` wire field.

**The source ID is `<topicId>:<messageUuid>` since 2026-10-14.** This replaced "The source ID remains `<conversationUuid>:<messageUuid>`". The route keys a request on the owner and the request ID, and it treats a differing room as a conflict. Rule 4 puts one shared parent into two topics. So one request ID cannot name both records, and the topic ID must scope it. The source ID is now the derivation input above.

**A rerun reads the derived message ID directly.** The importer recomputes the id from the topic id and the message UUID, then reads that id. One read proves a repeat. The sender, millisecond time and text match is not the repeat check, and it retires with this change. Two turns with those three values equal can no longer collapse.

`CHAT-zxrgxdlx` owns the route and store changes. It also owns the UUID-only import gate and the source-to-chat ID map. This work order must use that contract when it lands.

## Command change

Remove `--leaf-message`. Add an operator generation input. Remove the hyphen guard on `--topic`. Import all leaf paths in one command. Report topics created, messages imported, skipped turns, skipped blocks, skipped files, and missing parents.

Update `docs/SHELL-COMMAND-CONTRACT.md` when the command changes. Replace its hyphen rejection and `--leaf-message` requirement with the new name and generation rules.

## Acceptance

- A linear conversation imports into one topic in source order.
- A forked conversation imports every leaf path into a separate topic.
- Topic names use `<base>:<conversation-uuid>:<generation>:<leaf-uuid>`. The UUIDs keep their hyphens, and generation equals the operator input.
- A hyphenated `--topic` base is accepted.
- Shared ancestors appear in each topic that needs them.
- Records from another branch do not appear in a thread topic.
- A rerun adds no records and creates no duplicate topics.
- An additional leaf does not rename an existing topic.
- Each request ID stays within the 128-character limit.
- A shared parent receives one source ID in each topic that holds it.
- A missing parent is reported for its thread, and other complete paths still import.
- A missing user prevents all topic creation and message writes.
- A long key deployment refuses the import at startup. The error names the key type and the derivation.
- No `MessageService.pointer()` call occurs.
