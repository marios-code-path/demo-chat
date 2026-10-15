# Claude export import design

Issue: `CHAT-emwhnlay`.

The approved command runs in the existing JVM `chat-shell`. This issue permits a JVM build because `chat-shell` has no native profile. `CHAT-ulrkvfit` tracks that gap.

The command reads one `conversations.json` file. It selects one conversation by UUID or unique name. It uses the existing RSocket clients for user, room, and message routes. The command reads the Admin password from `CHAT_IMPORT_ADMIN_PASSWORD`. It never prints the password.

The parser returns one text message for each turn with text. It joins nonblank text blocks in source order with two newline characters between blocks. It uses flat `text` when `content` is absent or empty. It counts skipped attachments, files, nontext blocks, and turns with no text. An untitled conversation remains selectable by UUID. The parser preserves the message time and UUID. It rejects missing or malformed required fields.

## Observed export

The real export was inspected on 2026-10-10. It has no version field. Its top-level value is an array of 27 conversations. Each conversation has `uuid`, `name`, `summary`, `created_at`, `updated_at`, `account`, and `chat_messages` fields. The export contains 573 messages.

Each message has `uuid`, `text`, `content`, `sender`, `created_at`, `updated_at`, `attachments`, `files`, and `parent_message_uuid` fields. Message senders are `human` or `assistant`. Content blocks use `text`, `thinking`, `token_budget`, `tool_use`, or `tool_result` types.

Five conversations contain nine fork points. One parent has up to eleven child messages. The export has no current-leaf field. A conversation with multiple leaf paths therefore requires a leaf message UUID. The importer follows that message's parent links and imports only that path. A linear conversation needs no leaf selector.

Twenty-five parent UUIDs point to messages absent from the export. A selected path stops at its first absent parent. The command reports this boundary.

Forty messages contain a blank text block beside nonblank text. The parser skips blank text blocks before joining. The export has 22 `files` entries. Nineteen file entries match an attachment name in the same message. Count each matching file once. Three file entries add a distinct skip count. Three conversations have no messages.

The command accepts an optional `--leaf-message` UUID. Conversations with multiple leaf paths require this option. The command checks both returned handles for an exact match before any write. It rejects a room name with a hyphen. It creates a missing room only after the user checks. It submits imports serially. Each source ID contains the conversation UUID, a colon, and the message UUID. The route adds `import:`. The complete request ID cannot exceed 128 characters.

The route checks history by sender, millisecond time, and text. This check prevents duplicates on a serial rerun after restart. Two turns with all three values equal can collapse. Concurrent imports have no duplicate guarantee.

The sanitized fixture lives at `chat-shell/src/test/resources/import/claude-conversations.json`. Keep the real export outside the repository. Its observed schema has no version field. The importer counts files without counting a matching attachment twice.

The parser tests need no container. A running deployment must accept the fixture. `chat-shell messages` must show the expected order and sender. A second import must add no message. A missing user must stop before any write. The final `--ci` gate must rebuild the image.

## Corrections, 2026-10-14

Four passages above describe behavior that has changed since 2026-10-10.
This section states what replaced each one. The passages stay in place,
because they record what that session observed.

1. **"It rejects a room name with a hyphen."** That rule is gone since commit
   `d55e299e`. It rested on the Lucene name lookup, which split a name on a
   non-alphanumeric character. PR #224 made the topic name a whole-value
   match, so a colon and a hyphen are both safe in a name. The command accepts
   a hyphenated `--topic` base now.
2. **"The command accepts an optional `--leaf-message` UUID"** and **"A
   conversation with multiple leaf paths therefore requires a leaf message
   UUID."** That option is gone since commit `d55e299e`. The command imports
   every leaf path, and each path receives its own topic.
3. **"The route checks history by sender, millisecond time, and text."** That
   is the check today. `CHAT-zxrgxdlx` supersedes it. A rerun recomputes the
   derived message id and reads it directly, so the three-value match retires
   and two turns that share those values can no longer collapse.
4. **"Each source ID contains the conversation UUID, a colon, and the message
   UUID."** The source ID is `<topicId>:<messageUuid>` since 2026-10-14. The
   route keys a request on the owner and the request ID, and it treats a
   differing room as a conflict. A shared parent lands in two topics, so the
   topic ID scopes the request ID. Measured on 2026-10-14: the old form failed
   the second generation with `RequestConflictException`.

The replacement design is
`docs/superpowers/specs/2026-10-13-claude-thread-topic-import-work-order.md`.
