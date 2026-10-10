# Claude export import design

Issue: `CHAT-emwhnlay`.

The approved command runs in the existing JVM `chat-shell`. This issue permits a JVM build because `chat-shell` has no native profile. `CHAT-ulrkvfit` tracks that gap.

The command reads one `conversations.json` file. It selects one conversation by UUID or unique name. It uses the existing RSocket clients for user, room, and message routes. The command reads the Admin password from `CHAT_IMPORT_ADMIN_PASSWORD`. It never prints the password.

The parser returns one text message for each turn with text. It joins text blocks in source order with two newline characters between blocks. It uses flat `text` when `content` is absent or empty. It counts skipped attachments, nontext blocks, and turns with no text. An untitled conversation remains selectable by UUID. The parser preserves the message time and UUID. It rejects missing or malformed required fields.

The command checks both returned handles for an exact match before any write. It rejects a room name with a hyphen. It creates a missing room only after the user checks. It submits imports serially. Each source ID contains the conversation UUID, a colon, and the message UUID. The route adds `import:`. The complete request ID cannot exceed 128 characters.

The route checks history by sender, millisecond time, and text. This check prevents duplicates on a serial rerun after restart. Two turns with all three values equal can collapse. Concurrent imports have no duplicate guarantee.

The sanitized fixture lives at `chat-shell/src/test/resources/import/claude-conversations.json`. A real export remains outside the repository. Its observed schema and version require inspection before final acceptance. The inspection must confirm whether messages include a `files` array. If they do, the skip count must include those files.

The parser tests need no container. A running deployment must accept the fixture. `chat-shell messages` must show the expected order and sender. A second import must add no message. A missing user must stop before any write. The final `--ci` gate must rebuild the image.
