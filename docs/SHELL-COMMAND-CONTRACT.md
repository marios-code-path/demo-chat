# The shell command contract


The command surface of `chat-shell`, recorded before the Spring Shell 4
migration. **These names are the compatibility contract.** The migration must
not rename a command or an option. See CHAT-fxrwtvef.

## Why this file exists

Spring Shell 4 removes the annotation model this module was written in.
`@ShellComponent`, `@ShellMethod` and `@ShellOption` are absent from every
4.0.3 artifact, and `spring-shell-standard` stops at 3.4.3 on Maven Central.
Measured on 2026-09-18.

The migration goes to **programmatic** command registration rather than the new
annotations. Spring Shell 4 does not support declarative commands under GraalVM
native compilation, and this repository compiles its tools natively. See
https://github.com/spring-projects/spring-shell/issues/1229

## How a command name is produced today

**No command in this module declares its name.** Every `@ShellMethod` carries
one string, and that string is the `value` attribute, which is the help text.
The name comes from the method name.

`org.springframework.shell.Utils.unCamelify` supplies the rule, read from the
compiled 3.4.3 class on 2026-09-18: an upper case character becomes a hyphen
followed by its lower case form, and every other character passes through. So
`rootKeys` becomes `root-keys` and `whoami` stays `whoami`.

**The programmatic form must state each name, because nothing will derive it.**

## An option name is not un-camelified

`StandardMethodTargetRegistrar` in spring-shell-standard 3.4.3 calls
`unCamelify` exactly once, and it calls it on `Method.getName()`. Read from the
compiled class on 2026-09-18.

**So a command name is converted and an option name is not.** A parameter named
`topicName` is the option `--topicName`, with the camel case intact, while the
method `addTopic` is the command `add-topic`. The table below writes each one
as it is, and the two rules differ on purpose.

## The commands

The **By position** column names the option that the first bare word fills.
See `Positional input` below.

### LoginCommands

| Command | Method | Help | Options | By position |
|---|---|---|---|---|
| `bye` | `bye` | bye | none. Aliases `exit` and `quit` | none |
| `root-keys` | `rootKeys` | rootkeys | none | none |
| `whoami` | `whoami` | whoami | none | none |
| `login` | `login` | login | `--username` `String` required<br>`--password` `String` optional. The shell prompts for it when it is absent | `--username` |

### PubSubCommands

| Command | Method | Help | Options | By position |
|---|---|---|---|---|
| `send` | `send` | Send a Message | `--topic` `String` default `_`<br>`--userName` `String` default `_`<br>`--messageText` `String` required | `--messageText` |
| `listen` | `listen` | Listen to a topic | `--topic` `String` required | `--topic` |
| `hangup` | `hangup` | Stop listening to a topic | `--topic` `String` required | `--topic` |
| `messages` | `messages` | List the messages of a topic | `--topic` `String` required<br>`--limit` `Integer` optional | `--topic` |

### TopicCommands

| Command | Method | Help | Options | By position |
|---|---|---|---|---|
| `show-topics` | `showTopics` | show topics | none | none |
| `add-topic` | `addTopic` | Create a topic | `--userId` `String` default `_`<br>`--name` `String` required | `--name` |
| `topic-by-name` | `topicByName` | Topic by Name | `--userId` `String` default `_`<br>`--name` `String` required | `--name` |
| `join` | `join` | Subscribe to a topic | `--userId` `String` default `_`<br>`--topic` `String` required | `--topic` |
| `leave` | `leave` | unSubscribe to a topic | `--userId` `String` default `_`<br>`--topic` `String` required | `--topic` |
| `member-of` | `memberOf` | Show what topics user is subscribed to | `--userId` `String` default `_` | `--userId` |
| `list-members` | `listMembers` | Show Subscribers on a topic | `--topic` `String` required | `--topic` |

### UserCommands

| Command | Method | Help | Options | By position |
|---|---|---|---|---|
| `kv` | `kv` | Create a KeyValue | `--value` `String` required | `--value` |
| `get-k-v` | `getKV` | Get a KeyValue by Key ID | `--key` `T` required | `--key` |
| `all-k-v` | `allKV` | Get all KV | none | none |
| `key` | `key` | Create a Key | none | none |
| `add-user` | `addUser` | Add A User | `--name` `String` required<br>`--handle` `String` required<br>`--imageUri` `String` required | none |
| `users` | `users` | All Users | none | none |
| `find-user` | `findUser` | Find a user | `--handle` `String` required | `--handle` |
| `get-user` | `getUser` | Get a user | `--handle` `String` required | `--handle` |
| `passwd` | `passwd` | Change User Password | `--userId` `String` default `_`<br>`--password` `String` required | `--password` |
| `get-permissions-for-user` | `getPermissionsForUser` | Gets user Permissions | `--userId` `String` default `_` | `--userId` |
| `all-permissions` | `allPermissions` | Get all Perms | none | none |
| `add-permission` | `addPermission` | Add a User Permission | `--userId` `String` default `_`<br>`--targetUserId` `String` required<br>`--role` `String` required<br>`--expireTime` `String` required | none |

**27 commands and 33 options.** `ShellCommandContractTests` pins both counts,
every option and every positional argument. The migration table held 26
commands. `CHAT-rghaeqsa` added `messages`.

## Availability is not used, and the dead check is gone

**No command declares an availability gate**, by owner instruction during the
Spring Shell 4 migration.

`CommandsUtil.isAuthenticated()` used to return an `Availability` that no
command used. The module held zero `@ShellMethodAvailability` annotations,
and the method name did not match the `<command>Availability` convention
that Spring Shell reads instead, so nothing could have called it. One test
called it directly, which is the only thing that kept it alive.

Both are removed under `CHAT-fxrwtvef`. Adding a gate means adding it
deliberately, not reviving this.

**So no command is gated on login today.** The migration must not invent a
gate. Adding one would change behaviour under the name of a port. If the gate
is wanted, it is a separate decision with its own issue.

## What the migration must preserve

1. Every command name in the table, spelled exactly as written.
2. Every option name, spelled exactly as written.
3. Every default value, including the `_` defaults.
4. The help text of each command.
5. The absence of an availability gate.

**The owner changed rule 2 after the migration.** `CHAT-scoizkpm` replaced
`--topicName` and `--topicId` with `--topic` on 2026-10-03. See `One room
option` below. Every other name in the migration table stands.


## A default applies when the option is absent

**Until `CHAT-xdpcnrde`, every `_` default in the table was unreachable from a
typed line.** The owner found it on 2026-10-02: `join` asked for `--userId`,
and `send` asked for `--topicName`, `--topicId` and `--userName` together.

`DefaultCommandParser` adds only the typed options to the parsed input, and
`CommandContext.getOptionByLongName` reads the parsed input alone. So an
option that the caller leaves out is not in the context. `optionValue` in
`ShellCommandSupport.kt` failed there with
`the command declares no option named <name>`.

`optionValue` now reads the declared option from the registered command and
answers its default. `ShellParsedInputTests` parses a typed line through
`DefaultCommandParser` and pins this. `ShellCommandDispatchTests` cannot pin
it, because its context carries every declared option.

## Confirmations, aliases and the password prompt

`CHAT-dxkkzvrf` changed three behaviours. The owner found each one in a manual
session on 2026-10-02.

- **A command that changes state prints one confirmation line.** `add-topic`
  prints `Created room <name> (id <id>)`. `join` and `leave` print the room
  name. `listen` and `hangup` print the room id. Before, each printed nothing,
  so a success looked like a failure.
- **`exit` and `quit` run `bye`.** `CommandRegistry.getCommandByName` falls back
  to the aliases.
- **`login` prompts for the password when `--password` is absent.** The prompt
  reads through `InputReader.readPassword`, so the password does not echo and it
  does not enter the command history. **A typed `--password` still enters the
  history.**

## One room option

`CHAT-scoizkpm`. The owner met mixed room options in the session of
2026-10-02. `join`, `leave` and `list-members` took `--topicName`. `listen`
and `hangup` took `--topicId`. `send` took both.

**Every room command names the room through `--topic`.** The owner chose the
name on 2026-10-03. The value is a room name or a room id. `join`, `leave`,
`list-members`, `listen`, `hangup`, `messages` and `send` take it.

`add-topic` and `topic-by-name` keep `--name`, because each takes a name only.

`ShellRooms` reads the value. The rules are these:

1. The shell reads the value as a room name first.
2. When no room has that name, and the value is a valid id, the shell reads the
   room by its id.
3. When both reads miss, the command fails with
   `No room has the name or the id <value>.`
4. When the id read is refused, the command fails with
   `No room has the name or the id <value>, or you may not read that room.`
5. Any other error reaches the caller unchanged. A store failure or a lost
   connection is not an unknown room.

**A miss is a typed error, and the shell reads no message text.** The server
gives `NotFoundException` and `KeyVerificationException` the RSocket code
`0x404`, and the client decoder makes `CoreNotFound` from it. Before, every
server error arrived as `ApplicationErrorException` with code `0x201`. The
first version of `ShellRooms` read that type as a miss, so a real failure
showed as an unknown room. See `docs/REST-TOKEN-RELAY.md`.

**An unknown id is refused, not missed.** The `GET` check of `getRoom` reads
the root of the id before the service runs. An id that the registry does not
hold has no root, so the check answers false. The shell cannot tell that case
from a room that the caller may not read, so rule 4 names both. Measured on
2026-10-03 with `hangup 999999999999`.

A refusal of any later operation still reads `Access Denied`. So `messages`
and `listen` on a room that the caller may not read report the refusal of the
read, not of the lookup.

**A name wins over an id.** A person types names, and a room id is a long
number that a name rarely equals.

**`listen` stores its listener under the room id.** So `hangup lobby` and
`hangup <id>` stop the same listener. `hangup` stops a stored id with no
lookup, so a listener on a removed room still stops.

`ShellRoomsTests` pins the five rules, and it pins that a refused name lookup
reaches the caller unchanged. `ShellCommandContractTests` pins
`--topic` on every room command, and it refuses the two retired names.

## Positional input

`CHAT-lasqmeib`. The owner met `add-topic lobby` and `list-members <id>`
refused in the session of 2026-10-02.

**A command takes its main option as its first bare word.** So
`add-topic lobby` reads as `add-topic --name lobby`. The named option still
works. The **By position** column of the tables names the main option.

The main option follows one rule:

- A command with one required option takes that option by position.
- A command with one option and no required option takes that option by
  position. `member-of` and `get-permissions-for-user` are these.
- `add-user` and `add-permission` need several values. They take no value by
  position.

So `send` takes the text by position, as in `send --topic lobby "hello there"`.

`DefaultCommandParser` already added each bare word as an argument. No command
read one, so the old reader answered an empty text for the missing option.
`add-topic lobby` then sent a room with no name.

The shell refuses four inputs, each with one message:

| Input | Message |
|---|---|
| No main value | `add-topic needs name. Give it as the first argument or as --name.` |
| A main value two times | `Give topic one time, as --topic or as the first argument.` |
| Two bare words | `add-topic takes one argument. Put quotes around a value that has spaces.` |
| A missing required option | `add-user needs --imageUri.` |

The shell prints each message after `Unable to run command <name>:`.

**A password given by position enters the history.** `passwd` takes the
password by position under the rule above. A typed `--password` enters the
history in the same way.

`ShellParsedInputTests` runs the positional form of each of the 17 commands
through `DefaultCommandParser`, and it pins the four refusals.

## The messages command

`CHAT-rghaeqsa` added this command. `CHAT-bmmtojqm` adds the readable chat log format.

The owner tried `topics` and `messages` on 2026-10-02.
No command listed room messages at that time.
The owner named the command and chose its access check on 2026-10-03.

`messages <topic>` displays the stored messages of one room, oldest first.
Each message starts with `<time> | <sender handle> | <text>`.
The time uses the stored timestamp and the shell timezone, with the format `yyyy-MM-dd'T'HH:mm`.
The JSON and CBOR decoders retain supplied message timestamps.
Older payloads without timestamps retain the existing current-time fallback.
Additional text lines align under the text column.
Each distinct sender resolves once per command.
A missing user displays its key id.
Other lookup failures reach the caller.
A room with no message displays `No messages in <room name>.`

`messages <topic> --limit N` selects the newest N messages and displays them oldest first.
N must be a positive integer.
An absent limit displays all messages.

The read checks `SUBSCRIBE` on the room, as `listen` does.
A member, the room owner, and `Admin` may read.
Any other caller reads `Access Denied`.
The command does not use `GET`.
The shipped `GET` row on the `MessageTopic` root reaches every caller.

`listen` returns stored messages before live messages.
It does not mark the boundary between history and live messages.
`ChatMessageService.listMessages` returns the history and then completes.
The RSocket route is `message.message-list-topic`.
PR #178 added the REST route `GET /message/list/{id}`.
