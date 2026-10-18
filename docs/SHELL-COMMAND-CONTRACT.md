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
native compilation. `chat-shell` has no native profile. `CHAT-ulrkvfit` tracks
that gap. `CHAT-emwhnlay` permits the JVM importer command. See
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
| `root-keys` | `rootKeys` | rootkeys | none | none |
| `whoami` | `whoami` | whoami | none | none |
| `login` | `login` | login | `--username` `String` required<br>`--password` `String` optional. The shell prompts for it when it is absent | `--username` |

### PubSubCommands

| Command | Method | Help | Options | By position |
|---|---|---|---|---|
| `send` | `send` | Send a Message | `--topic` `String` default `_`<br>`--userName` `String` default `_`<br>`--messageText` `String` required<br>`--requestId` `String` default `_` | `--messageText` |
| `command-status` | `commandStatus` | Show the status of a message command | `--commandId` `String` required | none |
| `listen` | `listen` | Listen to a topic | `--topic` `String` required | `--topic` |
| `hangup` | `hangup` | Stop listening to a topic | `--topic` `String` required | `--topic` |
| `register-read` | `registerRead` | Read the register: its index and the newest recent entry of each topic | `--index` `String` default `register-index`<br>`--maxAgeHours` `String` default `24` | none |
| `post` | `post` | Post the text of standard input to a topic | `--topic` `String` required<br>`--requestId` `String` default `_` | `--topic` |
| `messages` | `messages` | List the messages of a topic | `--topic` `String` required<br>`--limit` `Integer` optional<br>`--format` `String` default `human`<br>`--since` `String` optional | `--topic` |

`send` submits a message command. It prints the request ID, the message ID,
the command ID, and the outcome. A `--requestId` of `_` makes a new ID. A
repeat of `send` with the printed request ID and the same text recovers the
same receipt and admits nothing new. The server binds the sender to the login,
so `send` sends no sender. `command-status` prints the state of each backend of
a command that the login owns. A command of another owner answers not found.

`post` sends the text of its command input to a topic, and it answers one JSON
line with `messageId`, `commandId`, `requestId`, and `outcome`. The text arrives
byte for byte: a missing final newline, a `\r\n` line end, and non-ASCII text
stay as sent. Empty input is refused. Only the shell modes give a command input,
so `post` in the interactive shell is refused. See `The shell modes` below.

`register-read` is the session-start read of the topic register, in one
process. It prints every pointer of the index, oldest first, as record lines.
It then prints the newest entry of each topic that a pointer names, if that
entry is younger than `--maxAgeHours`. A pointer reads `<fp-id> | <topic-name> |
<kind> | <title>`. A topic that two pointers name is read once. A pointer that
does not follow the form fails the read. **The command prints nothing until every
read has succeeded**, so a failure never leaves a partial register. An empty
index prints nothing. The hard limit is 30 seconds. In the shell modes it runs
from the launch of the process, so the JVM start counts. `main` arms a watchdog
before the application starts, so a startup that stalls in Java ends with status
124 too. **`chat-shell-cli` supervises the command from its own start**, so a stall
before Java starts, such as a stuck `chat-build`, ends with 124 as well. A timer
starts before any external command. Every external command runs as a job in its
own process group, and at the limit the supervisor sends TERM and then KILL to the
group, so a child that ignores TERM does not survive. It also sends KILL to the
group of the child when the child ends. The supervisor owns standard error and
leaves one `chat-shell: register-read passed its 30000 ms limit` line. It reads
the clock with `date` in a job. A clock that does not answer in 2 seconds counts
as a spent budget, and the command fails closed with 124. The launcher uses no
`perl`. If both the clock and the caller's stamp are unknown, the bound is 30
seconds from the start of the script. In the JVM, one path ends the command at the
limit: the watchdog, a spent budget, a read that times out, and an output that
finishes after the watchdog took over, or after the limit but before the watchdog
ran, all halt the process with 124 and write the line once. An output that
finishes late is never a success. A budget that startup
spent refuses the read. A budget that runs out during the read refuses the output.
Standard output is empty on 124. **One case differs:** if the limit passes while a
blocked reader holds up the write of the output, the watchdog halts the process
with 124, and standard output can hold part of the text. A caller must discard the
output of a command that does not exit with 0. The launch time is the
earlier of `CHAT_SHELL_LAUNCH_EPOCH_MS`, which `chat-shell-cli` sets, and the JVM
start. In the interactive shell the limit runs from the command.

### ClaudeImportCommands

| Command | Method | Help | Options | By position |
|---|---|---|---|---|
| `import-claude` | `importConversation` | Import one Claude conversation | `--file` `String` required<br>`--conversation` `String` required<br>`--topic` `String` required<br>`--generation` `String` required<br>`--human` `String` required<br>`--assistant` `String` required | none |

The import route needs an Admin session. Log in as Admin first, and the
command uses that session. Otherwise set `CHAT_IMPORT_ADMIN_PASSWORD`, and the
command logs in as Admin for the run and then restores the earlier login. With
neither, it refuses before any write. The result starts with the session it
used: `Imported as Admin (your session).` or
`Imported as Admin (CHAT_IMPORT_ADMIN_PASSWORD).` See `CHAT-wmfemntw`.

The command reads one conversation from the file. It
checks both user handles before it creates a topic. It imports every leaf path,
and each path receives its own topic. A topic name is
`<topic>:<conversation-uuid>:<generation>:<leaf-uuid>`. The operator supplies
the generation as a decimal value. The importer never computes one. A hyphen
and a colon are accepted in the name. The source ID of each record is
`<topic-id>:<message-uuid>`, and the route prefixes `import:`. The command
reports the topics created, the messages imported, the missing parents, and
the skipped content.

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

### HostCommandRegistrar

This registrar is in the `chat-shell-host` module, not in `chat-shell`. A
default shell build includes it. `chat-build shell --no-host-command` leaves it
out.

| Command | Method | Help | Options | By position |
|---|---|---|---|---|
| `!` | `HostCommand.run` | Run a host command with bash | none. The rest of the line is the host command | none |

See `The ! escape` below.

**30 commands and 47 options in `chat-shell`, and 31 commands in a default
build.** `ShellCommandContractTests` pins every command, option, and
positional argument of `chat-shell`. `HostCommandTests` pins the `!` command.
The migration table held 26 commands. `messages` and `command-status` raised
the count to 28. `CHAT-ybmlcdiw` removed `bye`. `CHAT-emwhnlay` adds
`import-claude`. `CHAT-jrlctimq` adds `!` in `chat-shell-host`, with no
option. `CHAT-jmnjvupr` adds `post` with two options, and it adds `--format`
and `--since` to `messages`. It also adds `register-read` with two options.

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
- **`exit` and `quit` leave the shell.** `InteractiveShellRunner` reads the two
  literals before it dispatches. See `No command exits the shell` below.
- **`login` prompts for the password when `--password` is absent.** The prompt
  reads through `InputReader.readPassword`, so the password does not echo and it
  does not enter the command history. **A typed `--password` still enters the
  history.**

## No command exits the shell

`CHAT-ybmlcdiw`. The `bye` command is removed. `exit` is the way out.

**The runner owns the exit literals.** `InteractiveShellRunner.doRun` reads the
typed line. It tests the line against `quit` and `exit` before any dispatch. It
prints `Exiting the shell` and leaves its loop. So `bye` never ran from a typed
line. The `exit` and `quit` aliases of `CHAT-dxkkzvrf` never ran either. A
scripted line reached the alias, and no interactive line did.

**The shell runs a web server, so the loop alone did not end the process.** The
shell carries `@EnableWebFlux`. The launch sets
`spring.main.web-application-type=reactive` and a management port. Spring
Boot's `NettyWebServer` starts a non-daemon thread named `server`. That thread
kept the JVM alive after `main` returned, and it kept the port bound.

**The launch sets `spring.shell.context.close=true`.** Spring Shell closes the
context when the shell ends. The close stops the server, so the process exits
and the port frees. `chat-build` sets the property on the `shell` service.
`golden/shell-client.flags` and `golden/shell-consul.flags` pin it.

**No command replaces `bye`.** A caller leaves through `exit` or `quit`.

## The ! escape

`CHAT-jrlctimq`. Design: `docs/superpowers/specs/2026-10-16-shell-escape-design.md`.

A line that starts with `!` runs the rest of the line with bash.

```
chat-shell:anonymous :> !jq -r '.[] | .uuid' ../conversations.json
```

- **The parser takes the line first.** `BangCommandParser` replaces the
  `CommandParser` bean. A line whose first non-blank character is `!` becomes
  the `!` command with one argument. The argument is the rest of the line,
  with the leading blanks removed and no other change. Every other line goes
  to `DefaultCommandParser` unchanged.
- **bash runs the argument.** `HostCommand` runs `bash -c <argument>`. The
  working directory is the directory of the shell process. For
  `just launch-shell`, that is `chat-deploy`. Standard input is `/dev/null`, so
  a program that reads input ends at once. Standard error joins standard
  output.
- **Each line prints as it arrives.** The command flushes the output after
  each line.
- **The exit status always prints.** After the output, the command prints
  `exit status <n>`, 0 included. No status ends the shell.
- **The failure lines.** `!` alone prints a usage line. No `bash` on the
  `PATH` prints `! needs bash on the PATH. This shell has none.` A failed start
  prints `! could not start bash: <reason>`.
- **History events are off.** JLine expanded `!jq` as a history event and
  removed backslashes, so the shell sets `DISABLE_EVENT_EXPANSION`. `!!` and
  `!n` do not work. The arrow keys and the `history` command still reach the
  history.
- **Ctrl-C stops the host command, and the shell continues.** While the
  command runs, `TerminalInterrupts` holds the INT handler of the terminal.
  The handler kills bash and every process that bash started. The command
  prints `! interrupted`, then `exit status 130` or `exit status 137`. 130
  means that the SIGINT of the terminal ended bash first. 137 means that the
  kill ended it first. A process that leaves the process tree of bash, such
  as one started with `setsid`, keeps running. `CHAT-wdokzwnj`.
- **Ctrl-C at the prompt still ends the shell.** Spring Shell stops its loop
  on any input error, and Ctrl-C at the prompt is one. The `!` work does not
  change this.
- **Interactive programs are out of scope.** `vim` and `less` get no
  terminal.

**A typed line reaches bash byte for byte. A script line does not.** Spring
Shell changes a `script -f` line before any parser receives it:

1. A line that ends with `\` and optional blanks continues on the next line.
   The `\` and the blanks are removed, and one blank joins the two lines.
2. A line that matches `\s*//.*` is a comment and does not run.
3. The trailing blanks of the line are lost. `Script` splits the line on
   single blanks, the runner joins the words again, and Java `split` drops
   trailing empty strings.

So a `!` line in a script file reaches bash unchanged only when it does not
end with `\` or a blank. `ShellEscapeConfigurationTests` pins this limit.

**The `!` command is a build option, and a shell build includes it by
default.** The owner set this on 2026-10-17. The code is in the
`chat-shell-host` module. The `shell-host` profile of `chat-deploy` adds that
module. `chat-build` adds the profile to every `shell` build, and
`--no-host-command` leaves it out. A shell without the module holds no `!`
code, and the shell does not run a `!` line.

**A chat-shell image with the `!` command holds bash.** The `shell-host`
profile builds on `docker.io/paketobuildpacks/ubuntu-noble-run:0.0.145`. A
shell image without the `!` command and the core images keep the tiny run
image, which holds no shell. `shell-scripts/test-shell-run-image.sh` checks
all three.

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

`messages <topic> --format records` prints one JSON line per message, oldest first.
Each line holds `id`, `topic`, `sender`, `timestamp`, and `text`.
The timestamp is the stored instant in UTC at its full precision.
The text is the body unchanged, and a pointer message shows its arrow text.
An empty result prints nothing.
`--format` takes `human` or `records`, and any other value is refused.
`--since <instant>` keeps the messages at or after an ISO-8601 instant, and `--limit` then keeps the newest N.
A `--since` value that does not parse is refused.

`listen` returns stored messages before live messages.
It does not mark the boundary between history and live messages.
`ChatMessageService.listMessages` returns the history and then completes.
The RSocket route is `message.message-list-topic`.
PR #178 added the REST route `GET /message/list/{id}`.

## The shell modes

`CHAT-jmnjvupr` adds two ways to run a command without the interactive prompt.
`chat.shell.mode` selects the mode. Its values are `interactive` (the
default), `one-shot`, and `resident`. Only `interactive` and `one-shot` exist.
Resident mode waits for an owner decision. See
`docs/superpowers/specs/2026-10-17-topic-register-design.md`.

**One-shot mode.** `shell-scripts/chat-shell-cli <command words>` starts a JVM,
logs in, runs the words as one command line, and exits with a status. Text for
the command goes on standard input. Run `just build-shell-cli` first. The
launcher needs `CHAT_SHELL_KEY_TYPE`. It starts silently: no banner, no log on
the console, and an empty standard error after a success.

**The exit status.** One table serves every mode.

| Status | Meaning |
|--------|---------|
| 0 | The command succeeded. |
| 1 | The command ran and failed. |
| 2 | The command line is not valid, or the mode refuses it. |
| 69 | The core is not available. |
| 75 | A post was sent, but no read showed it in time. Nothing returns it yet. |
| 76 | A resident request or response broke the protocol. Nothing returns it yet. |
| 77 | The login failed. |
| 124 | The command passed its time limit. `register-read` returns it. |

A failure writes one line on standard error: `chat-shell: <message>`.

**The refused forms.** The modes refuse `listen`, `hangup`, `script`, a blank
line, a first word that starts with `@`, and a first word that starts with `!`.
Each gives status 2. They stream, hold state across commands, read a script
file, or run a host command whose status Spring Shell does not see. The
interactive shell keeps `!` as it is.

**The login.** `CHAT_SHELL_USER` names the user. `CHAT_SHELL_PASSWORD_FILE`
names a file that holds the password. One trailing newline is removed and
nothing else. Set both or neither. One alone gives status 2. A password never
goes in a program argument. A wrong password gives 77. A core that is down gives
69.

**Command output.** Every command writes through the writer of its context.
`DirectOutputTests` fails on any direct print in the main source, except
`PubSubCommands.listen`.
