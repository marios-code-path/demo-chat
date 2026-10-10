# Claude export import implementation plan

Issue: `CHAT-emwhnlay`. Design: `docs/superpowers/specs/2026-10-09-claude-export-import-design.md`.

1. Add the sanitized fixture at `chat-shell/src/test/resources/import/claude-conversations.json`. Add parser tests at `chat-shell/src/test/kotlin/com/demo/chat/test/commands/ClaudeExportParserTests.kt`. Run the focused test and confirm that it fails because the parser is absent.
2. Add the parser at `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/ClaudeExportParser.kt`. Run the focused test and confirm that it passes. Cover typed text blocks, flat text, skip counts, and selection errors.
3. Add command tests at `chat-shell/src/test/kotlin/com/demo/chat/test/commands/ClaudeImportCommandsTests.kt`. Test exact handle checks, missing users before writes, room names, and serial imports. Confirm each new test fails before its code exists.
4. Add `ClaudeImportCommands.kt` and `ClaudeImportCommandsRegistrar.kt`. Update `ShellCommandContractTests.kt` and `docs/SHELL-COMMAND-CONTRACT.md`. Run focused shell tests.
5. Run the fixture against a running deployment. Read history with `chat-shell messages`. Import it again and compare history. Record the observed export schema after the real export arrives.
6. Run `shell-scripts/build-health.sh --ci` and confirm the image ID changes. Run `drift refs` for changed documentation, review the bound prose, and run `drift check`.
