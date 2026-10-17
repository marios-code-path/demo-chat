# Shell escape: run a host command from chat-shell with `!`

Issue: `CHAT-jrlctimq`. Parent: `CHAT-aqpcacwv`. Status: draft for owner review.

## Goal

An operator types `!<command>` in chat-shell. The shell gives the rest of the
line to bash, exactly as typed, and prints the output. The shell keeps running
after the command ends, whatever its exit status.

The need came on 2026-10-16. The owner listed the conversation UUIDs of a
Claude export with `jq` outside the shell, then returned to the shell to run
`import-claude`.

## Owner decisions (2026-10-16)

1. A general `!` escape, not a narrow export-listing command.
2. Scope: pipes and filters with multi-line output. Interactive programs, such
   as `vim` or `less`, are out of scope.
3. The exit status of a host command has no effect on the shell.
4. The working directory is the directory of the shell process. For
   `just launch-shell` that is `chat-deploy`.
5. A chat-shell container image holds `/bin/bash`.

## Measured facts

All against Spring Shell 4.0.3 and jline-reader 3.30.6, read from the jars and
sources in `~/.m2` on 2026-10-16.

- **The parser bean reaches the interactive runner.**
  `JLineShellAutoConfiguration.jlineShellRunner` takes the `CommandParser`
  bean, and `ShellRunnerAutoConfiguration.commandParser` is
  `@ConditionalOnMissingBean`. The non-interactive runner takes the same bean.
  `DefaultJLineShellConfiguration` builds its own parser, but nothing imports
  or scans it in this application.
- **The default parser cannot carry a shell line.** `DefaultCommandParser`
  splits on whitespace and keeps only a double-quoted string as one word. A word
  that starts with `-` becomes an option. `ParsedInput` keeps no raw line. So a
  plain command named `!` would get `jq -r '.[] | .uuid'` in broken pieces.
- **History event expansion corrupts a `!` line.** The `LineReader` bean does
  not set `DISABLE_EVENT_EXPANSION`. `DefaultExpander` rejects `!jq ...` with
  "event not found". A real `LineReader` with the Spring Shell
  `ExtendedDefaultParser` returned `'.[] | "\(.uuid)\t\(.name)"'` as
  `'.[] | "(.uuid)t(.name)"'`. With `DISABLE_EVENT_EXPANSION` it returned the
  line byte for byte. With a matching history entry, the expander would put an
  earlier command in place of the typed one.
- **`script -f` uses its own parser.** `Script` builds a `DefaultCommandParser`,
  and it has `setCommandParser`. So `!` lines in a script file need that setter.
- **The run loop.** `InteractiveShellRunner.doRun` reads a line, calls
  `commandParser.parse`, and runs the named command from the registry. A parse
  error prints "Error while parsing command" and the loop continues.
- **The container images.** Builds use the Spring Boot default builder. The
  run image of `cassandra-core-service-rsocket:0.0.1` is
  `docker.io/paketobuildpacks/ubuntu-noble-run-tiny:0.0.138`, which holds no
  `/bin/sh` and no `/bin/bash`.
  `docker.io/paketobuildpacks/ubuntu-noble-run:0.0.145` holds `/bin/bash`
  5.2.21, and `/bin/sh` links to dash. No chat-shell image exists on the
  owner's machine. `chat-build shell --build` produces one named `chat-shell`.

## Design

Four units. Each one has one job and its own test.

### 1. `BangCommandParser` (chat-shell)

A `CommandParser` bean that wraps a `DefaultCommandParser`.

- A line whose first non-blank character is `!` becomes a `ParsedInput` with
  the command name `!` and one argument. The argument value is the rest of the
  line after the `!`, with leading blanks removed, and with no other change.
- A line with only `!` becomes the command `!` with no argument.
- Every other line goes to the `DefaultCommandParser` unchanged.

The bean replaces the default bean through `@ConditionalOnMissingBean`. The
interactive runner, the non-interactive runner, and `script -f` all use it.

### 2. `HostCommand` (chat-shell)

A command registered with the name `!`, in a new group `Host`.

- With no argument, it returns a usage line: `Usage: !<command>. Runs the rest
  of the line with bash in <working directory>.`
- With an argument, it runs `bash -c <argument>` through `ProcessBuilder`.
  - The working directory is the directory of the shell process
    (`user.dir`).
  - The environment is the environment of the shell process.
  - Standard input is the null device, so a program that reads input ends at
    once and cannot hold the shell.
  - Standard error goes into standard output, so the lines keep their order.
  - Each output line goes to the command output writer when it arrives. A
    long command shows its output as it runs.
- A non-zero exit status prints one line: `exit status <n>`. Status 0 prints
  nothing. The command always returns normally, so the shell keeps running.
- If no `bash` is on the `PATH`, it prints `! needs bash on the PATH. This
  shell has none.` and returns normally.

`bash` comes from the `PATH`. The design adds no shell selection.

### 3. Event expansion off (chat-shell)

A `BeanPostProcessor` sets `LineReader.Option.DISABLE_EVENT_EXPANSION` on the
`LineReader` bean. The `lineReader` bean is not `@ConditionalOnMissingBean`,
so a replacement bean would collide. A post-processor changes one option and
leaves the rest of the bean as Spring Shell builds it.

The same unit sets the `BangCommandParser` on the `Script` command bean.

`!!` and `!n` history events stop working. The shell history remains, through
the arrow keys and the `history` command.

### 4. A chat-shell image with bash (chat-deploy)

The `shell` profile of `chat-deploy` names
`docker.io/paketobuildpacks/ubuntu-noble-run:0.0.145` as the run image of its
image build. Other images keep the default tiny run image.

## Data flow

```
typed line -> LineReader (no event expansion) -> BangCommandParser
  starts with "!"  -> ParsedInput("!", [rest of line]) -> HostCommand
                      -> bash -c "<rest>" in user.dir, stdin null
                      -> each line to the output writer, then "exit status n" if n != 0
  otherwise        -> DefaultCommandParser -> the existing command
```

## Errors

| Case | Result |
|------|--------|
| `!` alone | the usage line |
| no `bash` on the `PATH` | `! needs bash on the PATH. This shell has none.` |
| non-zero exit status | the output, then `exit status <n>` |
| `bash` fails to start | `! could not start bash: <reason>` |
| a program waits for input | it reads end of input at once |

No case ends the shell.

## Tests

Each test fails before its change. No test reaches the owner's deployment.

- `BangCommandParserTests`: a `!` line keeps quotes, pipes, backslashes, `$`,
  and options byte for byte. Leading blanks before and after `!` are removed.
  `!` alone gives no argument. A non-`!` line gives the same `ParsedInput` as
  `DefaultCommandParser`.
- `HostCommandTests`: `echo a; echo b` gives two lines. A pipe with `tr`
  works. `exit 3` prints `exit status 3` and returns. `cat` returns at once.
  `pwd` prints `user.dir`. Output from standard error appears. An empty `PATH`
  gives the no-bash line.
- `EventExpansionTests`: a `LineReader` from the post-processor, with a dumb
  terminal and the Spring Shell parser, returns the jq line of the facts above
  byte for byte. Without the post-processor, the same test fails. This is the
  probe of 2026-10-16 as a test.
- `ScriptBangTests`: `script -f` on a file with a `!echo` line runs it.
- `ShellCommandContractTests` and `docs/SHELL-COMMAND-CONTRACT.md`: the
  contract adds the `!` command, so the counts move from 28 to 29 commands.
- The image: build `chat-shell` once with `chat-build shell --build`, then run
  `docker run --rm --entrypoint /bin/bash chat-shell -c 'echo ok'`. Record the
  image id.
- Gates: `build-health.sh` in both modes, `drift check`, and
  `git diff --check`.

## Docs

- `docs/SHELL-COMMAND-CONTRACT.md`: the `!` command, the parser rule, and the
  lost `!!` history events.
- `docs/BUILD.md`, at the shell section: one example,
  `!jq -r '.[] | .uuid' ../conversations.json`, with the working directory
  stated.

## Out of scope

- Interactive programs, and any terminal handover.
- A way to stop a running host command. Ctrl-C behavior is not designed here.
- Shell selection other than `bash`.
- Any change to a core image.

## Unverified

- A `runImage` override with the default Spring Boot builder is common
  practice, but this repository has not built one. The image test above
  proves it or refutes it.
- The `CommandHighlighter` can mark `!jq` as an unknown command, because the
  first word is not a registered name. This is display only, and the design
  does not change it.
