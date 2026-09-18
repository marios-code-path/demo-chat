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

## The commands


### LoginCommands

| Command | Method | Help | Options |
|---|---|---|---|
| `bye` | `bye` | bye | none |
| `root-keys` | `rootKeys` | rootkeys | none |
| `whoami` | `whoami` | whoami | none |
| `login` | `login` | login | `--username` `String` required<br>`--password` `String` required |

### PubSubCommands

| Command | Method | Help | Options |
|---|---|---|---|
| `send` | `send` | Send a Message | `--topicName` `String` default `_`<br>`--topicId` `String` default `_`<br>`--userName` `String` default `_`<br>`--messageText` `String` required |
| `listen` | `listen` | Listen to a topic | `--topicId` `String` required |
| `hangup` | `hangup` | Stop listening to a topic | `--topicId` `String` required |

### TopicCommands

| Command | Method | Help | Options |
|---|---|---|---|
| `show-topics` | `showTopics` | show topics | none |
| `add-topic` | `addTopic` | Create a topic | `--userId` `String` default `_`<br>`--name` `String` required |
| `topic-by-name` | `topicByName` | Topic by Name | `--userId` `String` default `_`<br>`--name` `String` required |
| `join` | `join` | Subscribe to a topic | `--userId` `String` default `_`<br>`--topicName` `String` required |
| `leave` | `leave` | unSubscribe to a topic | `--userId` `String` default `_`<br>`--topicName` `String` required |
| `member-of` | `memberOf` | Show what topics user is subscribed to | `--userId` `String` default `_` |
| `list-members` | `listMembers` | Show Subscribers on a topic | `--topicName` `String` required |

### UserCommands

| Command | Method | Help | Options |
|---|---|---|---|
| `kv` | `kv` | Create a KeyValue | `--value` `String` required |
| `get-k-v` | `getKV` | Get a KeyValue by Key ID | `--key` `T` required |
| `all-k-v` | `allKV` | Get all KV | none |
| `key` | `key` | Create a Key | none |
| `add-user` | `addUser` | Add A User | `--name` `String` required<br>`--handle` `String` required<br>`--imageUri` `String` required |
| `users` | `users` | All Users | none |
| `find-user` | `findUser` | Find a user | `--handle` `String` required |
| `get-user` | `getUser` | Get a user | `--handle` `String` required |
| `passwd` | `passwd` | Change User Password | `--userId` `String` default `_`<br>`--password` `String` required |
| `get-permissions-for-user` | `getPermissionsForUser` | Gets user Permissions | `--userId` `String` default `_` |
| `all-permissions` | `allPermissions` | Get all Perms | none |
| `add-permission` | `addPermission` | Add a User Permission | `--userId` `String` default `_`<br>`--targetUserId` `String` required<br>`--role` `String` required<br>`--expireTime` `String` required |

**26 commands and 32 options.** The option count matches a
raw count of `@ShellOption` in the same sources, which is the cross check that
the table is complete.

## Availability is declared and never applied

`CommandsUtil.isAuthenticated()` returns an `Availability`, and **no command
uses it**. The module holds zero `@ShellMethodAvailability` annotations, and
the method name does not match the `<command>Availability` convention that
Spring Shell reads instead. One test calls it directly.

**So no command is gated on login today.** The migration must not invent a
gate. Adding one would change behaviour under the name of a port. If the gate
is wanted, it is a separate decision with its own issue.

## What the migration must preserve

1. Every command name in the table, spelled exactly as written.
2. Every option name, spelled exactly as written.
3. Every default value, including the `_` defaults.
4. The help text of each command.
5. The absence of an availability gate.

