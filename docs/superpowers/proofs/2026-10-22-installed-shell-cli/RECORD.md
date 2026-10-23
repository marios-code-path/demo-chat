# Installed shell CLI live proof

Date: 2026-10-23 UTC. Source commit: `0749fff3aa08129153043e4aa00ef48f207013cb`.

## Install

Run from the `chat-bjqzvsoe-cli-runtime` worktree:

```text
$ node-9/install
node-9 install: build-shell-cli-lib: 149 jars, 65893823 bytes in /opt/demo-chat/.node9-install-musJ2U/lib
node-9 install: installed in /opt/demo-chat/node-9
```

Status: `0`. The installed marker names source commit `0749fff3aa08129153043e4aa00ef48f207013cb`.
The host Java version is `openjdk version "25" 2025-09-16`.

## Development identity check

```text
$ CHAT_SHELL_USER=Theta CHAT_SHELL_PASSWORD_FILE=/opt/demo-chat/node-9/secrets/Theta.password /opt/demo-chat/node-9/bin/chat-shell-cli whoami
91b799b0-cda6-11f1-bae2-25daa21d179d : Theta | THETA AGENT | chatimg://theta.png
```

Status: `0`. Standard error: zero bytes. Theta is the development identity for this proof.
The command selects its identity and password file at run time.

## Register read

```text
$ /opt/demo-chat/node-9/bin/register read --as Theta
register: chat-shell: No room has the name or the id register-index.
```

Status: `1`. The command wrote the error to standard error.
The Sigma session read returned the same error and status.
The register remains incomplete. No successful register read is proven.

## Isolated run

Run from `/private/tmp` with `HOME=/nonexistent` and `PATH=/usr/bin:/bin`.
Remove `CHAT_SHELL_CLI` and `CHAT_SHELL_LIB` from the environment.

```text
$ /opt/demo-chat/node-9/bin/register read --as Theta
register: chat-shell: No room has the name or the id register-index.
```

Status: `1`. This run reached the same register error without the repository, Maven, or a usable home directory.

```text
$ CHAT_SHELL_USER=Theta CHAT_SHELL_PASSWORD_FILE=/opt/demo-chat/node-9/secrets/Theta.password /opt/demo-chat/node-9/bin/chat-shell-cli whoami
91b799b0-cda6-11f1-bae2-25daa21d179d : Theta | THETA AGENT | chatimg://theta.png
```

Status: `0`. Standard error: zero bytes.
This run proves the installed CLI can authenticate without the repository, Maven, or a usable home directory.
The register read remains unproven. Stage 4A Task 10 owns the import and its live register read.

## Gate ruling after review

Source commit `ce80a45cffcf40803627644896c00a20b74c70d9` replaced the installed runtime.
The install returned status `0` with 149 jars and 65,893,845 bytes.
The installed marker names that source commit and Java 25.
The options file records `# java /usr/bin/java`.

The exact Theta `whoami` command returned status `0` and the identity shown above.
Standard error had zero bytes.
The isolated `whoami` command returned the same result with `HOME=/nonexistent` and `PATH=/usr/bin:/bin`.
The launcher returned status `2` for `CHAT_SHELL_JAVA=/no/such/java` and named the missing path.

`CHAT-bjqzvsoe` requires the installed `whoami` proof.
This proof meets that requirement.
The import creates `register-index` after this prerequisite passes.
The failed reads above remain evidence of the pre-import host state, not a failure of the CLI acceptance gate.
