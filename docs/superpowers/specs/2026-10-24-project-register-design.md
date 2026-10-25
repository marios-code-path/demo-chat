# Project registers on node 9

Status: approved after review on 2026-10-25.
See CHAT-jmnjvupr. Supersedes the single-register rule of
`docs/superpowers/specs/2026-10-17-topic-register-design.md`.

## Intent

Node 9 is a reusable service. It belongs to no host and no project. It serves
Theta, Sigma, and Mario across four projects in flight: dagdir, waveplan,
demo-chat, and stability-toys.

The register is a hand-off record. A hand-off belongs to one project. Today
one fixed room, `register-index`, holds every pointer. A second project in the
same install would share pointer space and topic names with the first.

This design gives each project its own register room inside one install.
Nothing crosses installs.

## Why now

No register exists in a production setting. Every install is disposable.
The owner declared the slate clean on 2026-10-24.

## Goals

- One register room per project, named by the operator.
- One install holds many project registers.
- One environment variable names the register for every command.
- One command creates a register from an existing `forward-register.md`.
- Install stays installation-only.

## Non-goals

- Migration of any existing `register-index` room. None is in production.
- Sharing one register between two installs.
- A separate Git repository for node 9.
- Per-project membership. The member set comes from `secrets.env`.

## Anti-goals

- A silent default register name.
- Install creating, reading, or naming a register room.
- A command that guesses the project from a directory name.

## Constraints

- The room topic rule stays `^[a-z0-9][a-z0-9-]{0,63}$`.
- A topic name holds at most 64 characters.
- One source section holds at most 16,384 bytes. The importer applies the
  same limit today.
- The pointer line form stays `<key> | <topic> | <metadata> | <title>`.
- The owner proof stays one `*` permission row on the room.
- The wrapper keeps its 30 second limit.
- No command prints a secret value.

## Owner decisions

Recorded 2026-10-24.

1. An environment variable names the register. It lives in `.envrc` of the
   project checkout. The examples are `NODE_9_REGISTER_TOPIC_NAME` and
   `NODE_9_REGISTER_TOPIC_ID`.
2. Topic names carry the full register name as a prefix.
3. Install does not create a register. Install is installation alone. The
   register is the application concern.
4. The slate is clean. Existing installs are disposable.
5. Each install holds its own copy of every project register.
6. Entry granularity is one section heading plus the paragraph that follows
   it. One such pair is one message. The split does not change how a register
   is queried.

## Contract

### The register name

`NODE_9_REGISTER_TOPIC_NAME` holds the room name, for example
`demo-chat-register`.

`NODE_9_REGISTER_TOPIC_ID` holds the room id, for example
`02d3f900-cf22-11f1-9f49-7184040233df`.

Rules:

- A read command needs one of the two variables.
- A write command needs the name. It mints topic names, and an id gives no
  prefix.
- The name wins when both variables are set.
- No variable stops the command with status 2. The line names both variables.
- `register-create` does not read the variable. It takes the name as an
  argument.

### Topic names

One register owns these topic shapes:

| Purpose | Shape |
|---------|-------|
| Pointer index | `<name>` |
| Entry topic | `<name>-fr-<short>` |
| Snapshot topic | `<name>-fr-snapshot-<12 hex>` |

The name holds at most 37 characters. That bound leaves room for
`-fr-snapshot-` and twelve hex characters inside the 64 character limit.

### `register-create`

```
register-create <name> [<path>] --as <owner>
```

This is a new script, `node-9/bin/register-create`. It runs from an installed
home. It reads no register variable.

`--as` is required. There is no default owner. A missing owner stops the
command with status 2. The importer applies the same rule to `apply` and
`verify`.

The script builds the mapping and calls the importer functions that already
run `pack`, `apply`, and `verify`. It does not copy that code. The mapping
holds one entry per section, with the entry range cut from the source file.

Order:

1. Check the toolchain guard and the name rule.
2. Check the path when one is given. The path must be absolute, a regular
   file, not a link, and readable.
3. Derive the entries from the file.
4. Read the owner credentials.
5. Create the room `<name>` when it is absent.
6. Refuse a room that holds messages and has no matching receipt.
7. Prove ownership. The `*` row of the room must name the owner.
8. Join the room as the owner and as every member of `secrets.env`.
9. Post the snapshot sections, the entry sections, and the pointer lines.
10. Read back every pointer and the whole register.

Without a path, steps 1, 4, 5, 7, and 8 run alone. The room stays empty.

A repeated command resumes through the receipt directory of the importer.

### Entry derivation

The splitter cuts the file at each line that starts with `## `.

- Section 0 is the preamble. It is not an entry.
- Each later section gives one entry. The entry text is the heading line and
  the first paragraph below it, up to the next blank line.
- The short name is a slug of the heading text. Lowercase letters and digits
  stay. Every other run of characters becomes one dash. A leading or trailing
  dash goes.
- The key is the first `CHAT-[a-z0-9]+` token in the heading. Without such a
  token, the key is `section-<index>`.
- The title is the heading text after `## `. It must pass the title rule.

Short names are assigned in section order:

- The first section with a slug keeps that slug.
- A later section with the same slug takes `<slug>-<index>`.
- A suffixed name that another section already holds stops the command with
  status 2. The line names both section indexes.
- An empty slug stops the command with status 2. The line names the section
  index.
- A short name that breaks the topic rule or the length bound stops the
  command with status 2.

The snapshot topic `<name>-fr-snapshot-<12 hex>` belongs to the source
hash. An entry short name equal to `snapshot-<12 hex>` of the source hash
stops the command with status 2. The line names the section. An entry never
takes the snapshot topic.

Two size rules apply:

- One heading section must hold at most 16,384 bytes. The importer applies
  the same limit to every section, and the snapshot keeps each section as one
  part. A larger section stops the command with status 2. The line names the
  section. No command cuts a section.
- The entry text is the heading and the first paragraph. A heading section
  that passes the limit always fits an entry.

### `register open`, `register post`, `register read`

- The index room is the name from the variable, or the id when no name is set.
- `open` builds `<name>-fr-<short>`. The short name holds one or more
  lowercase letters, digits, or dashes. It starts with a letter or a digit.
  An empty, uppercase, or other short name stops with status 2.
- `open` and `post` join the index room when the read is denied.
- `open` and `post` read pointers from the room named by `<name>`. The record
  filter compares against that name, not against a fixed string.
- `read` passes `--index <value>` to `register-read`. The shell resolves the
  value as a room name first, then as a room id (`ShellRooms.room`). So an id
  works here. The read still parses the pointer records of that room to find
  the entry topics (`PubSubCommands.readRegister`).
- `read <topic>` keeps its shape. The pointer gives the topic.
- A write stops with status 2 when only the id is set. The error line asks
  for the name.

### `register-import`

- `apply` and `verify` take `--register <name>`. The default is the variable.
- `ensure_index`, `index_message_count`, `topic_room_id`, and `join_all` use
  that name.
- Every entry topic starts with `<name>-fr-`.
- The snapshot topic is `<name>-fr-snapshot-<12 hex>`.
- The receipt directory is per register.
- The ownership check compares the `*` row to the owner of `--as`. It no
  longer compares to a fixed name. See CHAT-sufkkqon.

### Name collisions

A register owns every room that starts with `<name>-fr-`.

`register-create` refuses a name when either case holds:

- A room starts with `<name>-fr-`.
- The name starts with `<other>-fr-` for an existing register `<other>`.

The refusal stops with status 1 and prints the room that holds the space.

### Status codes

| Status | Cause |
|--------|-------|
| 0 | Success |
| 1 | A room holds messages and no receipt matches, a pointer belongs to another key, or a source changed |
| 2 | Missing variable, bad name, bad path, bad topic, a symbolic link, a guard failure |
| 75 | A post has no receipt |
| 77 | Credentials are absent |
| 124 | The wrapper limit passed |

## My calls

These are builder choices. Revisit any of them.

1. The name holds at most 37 characters.
2. The key falls back to `section-<index>` when the heading holds no issue id.
3. The short name is a slug of the heading. A repeated short takes the
   section index. A second collision and an empty slug are refusals, not
   silent renames.
4. The importer section limit of 16,384 bytes stands. This design does not
   relax it.
5. The preamble is not an entry.
6. The path is optional. Without it, the command creates an empty register.
7. The name wins when both variables are set.
8. `chat-shell` loses the `register-index` default of `register-read`.
9. A repeated `register-create` resumes through the importer receipts.
10. `--as` is required. No default owner exists.
11. An entry short name never takes the snapshot topic.

## What is left to the builder

- One helper in `lib.sh` resolves the register value for every wrapper.
- The wording of the refusal lines.
- The receipt directory layout under the installed home.
- The offline test list. It covers every refusal above and two registers in
  one install.
- Documentation. `node-9/README.md` gets the register section. The 2026-10-17
  design gets one superseded note.
