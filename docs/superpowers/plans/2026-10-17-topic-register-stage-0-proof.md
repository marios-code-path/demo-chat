# Topic register, stage 0: the one-shot proof run

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task. `AGENTS.md` forbids subagent-driven development, so do not use it. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run `chat-shell` once per command against a disposable core, with redirected streams, and record what it does. The stage 1 plan uses that record to fix its interface.

**Architecture:** A disposable memory core runs on host ports 6990 and 6991. The core and every shell process run inside a macOS sandbox that blocks outbound connections to each port of the live services, so no process of the proof can reach node 16. A harness starts the shell JVM directly, from artifacts built at the proof commit. It runs one case per process and records each stream, the exit status, the time, and the connections. A proof record states the results and their effect on stage 1. This stage changes no product code.

**Tech Stack:** Bash, `sandbox-exec`, Java 25, Spring Shell 4.0.3, the `chat-deploy` shell launch, the `chat-deploy-memory` core.

**Spec:** `docs/superpowers/specs/2026-10-17-topic-register-design.md`, sections "Build order" (stage 0), "chat-shell as a command tool", and "Unverified".

## Global Constraints

- **No process of the proof can reach node 16.** Isolation comes before the first connection. Detection after a connection does not count.
- Do not use `chat-deploy`, `docker compose`, or `just deploy-*`.
- The disposable core uses `CORE_PORT=6990` and `CORE_MGMT_PORT=6991`. Key type `uuid`. Node id `0`.
- The shell feature set is the default one: `--host-command` is in, and the Maven profiles are `deploy,shell,shell-host,client-backend`.
- Maven builds the artifacts. Maven is never the measured shell process.
- One Maven build per worktree. Log each Maven run to a file, and read only its exit status and summary lines.
- Proof scripts go in `docs/superpowers/proofs/2026-10-17-one-shot/`. Raw stream files go in a scratch directory `$S` outside git.
- Prose follows ASD-STE100 strict mode.
- Every merge needs the owner's named `--admin` bypass.

## The isolation

A measurement on 2026-10-17 tested `sandbox-exec` with a read-only probe.

- A rule `(deny network-outbound (remote ip "localhost:6791"))` blocked `curl`. **It did not block a JVM.** A Java `SocketChannel` reached 6790, 6791, 8500, 9042, 6379, and 9000 inside that sandbox. Java opens a dual-stack socket, and the `localhost` filter does not match it.
- A rule with the any-host form, `(remote ip "*:6790")`, blocked the JVM with `java.net.SocketException: Operation not permitted`. Port 8500, which the rule did not name, still connected.
- A `localhost` rule with `-Djava.net.preferIPv4Stack=true` also blocked the JVM.

So the profile uses the any-host form alone:

```
(version 1)
(allow default)
(deny network-outbound (remote ip "*:<port>") ...)
```

`<port>` takes each host port that a running container publishes. Task 1 measures that list. A child process inherits the sandbox, so the rule also covers Maven and the Java core that `chat-build` starts.

## Questions the proof answers

| ID | Question | Design part |
|----|----------|-------------|
| Q1 | With `spring.shell.interactive.enabled=false`, does the shell run one command and then exit? | Unverified 1, part 3 |
| Q2 | What do standard output and standard error hold besides command output? | Part 1 |
| Q3 | What exit status does the process give for success, a failed command, an unknown command, and a failed login? | Part 2 |
| Q4 | Does a first argument `@<file>` run a script? | Part 2, the `@` refusal |
| Q5 | How long does one run take, from process start to exit? | Decision 20 |
| Q6 | Does `CORE_PORT=6990` send the RSocket connection to 6990? | Part 5, unverified 3 |
| Q7 | Does the current `login` prompt read its password from the process standard input? This tests the current prompt path alone. Stage 1 must prove separately that `post` reads multi-line input unchanged. | Unverified 6 |
| Q8 | What does the shell do today, in interactive mode, with redirected standard input? | Owner report of 2026-10-17 |

## Review Focus

1. **The sandbox rule does not cover the JVM.** The `localhost` form failed for Java. Task 1 tests the exact profile with a Java probe before the first shell case. A denied port that connects stops the proof.
2. **Stale project jars.** A classpath can resolve `com.demo` jars from `~/.m2` that differ from the proof commit. Task 1 builds them at the commit and compares each jar on the classpath with the jar that the build made.
3. **The core outlives the proof.** `chat-build` starts Maven, and Maven starts the Java core. Task 1 starts the core in its own process group, and stops that group.
4. **A case does not exit.** The reactive web server on 9101 or a JLine terminal can keep a process alive. Each case runs in its own process group, with a 60-second limit. At the limit, the harness stops the group. It then confirms that the group holds no process and that 9101 has no listener.
5. **Secrets in the record.** The case commands hold the `Admin` password of the disposable core. The record shows `<password>` in place of it.

---

### Task 1: The isolation, the artifacts, and the disposable core

**Files:**
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/isolation.sb.sh`
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/Probe.java`
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/build.sh`
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/core.sh`
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/launch.sh`

**Interfaces:**
- Produces: `isolation.sb.sh <scratch-dir>` writes `<scratch-dir>/isolation.sb`, the sandbox profile. It takes the denied ports from `docker ps --format '{{.Ports}}'`, one rule per published host port. A range such as `6790-6791->6790-6791/tcp` gives one rule per port in it. TCP and UDP ports both count. It also writes `<scratch-dir>/denied-ports`, one port per line.
- Produces: `Probe.java <port>...` tries a `SocketChannel` connect to `127.0.0.1:<port>` for each port, and prints `<port> connected` or `<port> denied: <exception>`.
- Produces: `build.sh <scratch-dir>` builds the artifacts and writes `<scratch-dir>/shell.cp`, `<scratch-dir>/artifacts.tsv`, and `<scratch-dir>/commit`.
- Produces: `core.sh start|stop <scratch-dir>`. `start` writes `<scratch-dir>/core.pgid` and `<scratch-dir>/service.password`.
- Produces: `launch.sh <scratch-dir> [shell-args...]` starts one shell JVM inside the sandbox. Standard input, output, and error pass through, and it exits with the JVM exit status. The plan does not assume that `sandbox-exec` replaces itself with Java.

- [ ] **Step 1: Check the preconditions, and write the profile**

Run: `for p in 6990 6991 9101; do lsof -nP -iTCP:$p -sTCP:LISTEN; done`, then `isolation.sb.sh "$S"`, then `docker ps --format '{{.Names}} {{.Status}}' > "$S/docker-before"`.
Expected: no listener on 6990, 6991, or 9101. `denied-ports` holds at least 6790 and 6791.

- [ ] **Step 2: Prove the isolation before any shell runs**

Run: `sandbox-exec -f "$S/isolation.sb" java docs/superpowers/proofs/2026-10-17-one-shot/Probe.java $(cat "$S/denied-ports")`
Expected: every line says `denied: java.net.SocketException: Operation not permitted`. Any `connected` line stops the proof. Report it to the owner.

- [ ] **Step 3: Build the artifacts at the proof commit**

`build.sh` runs, with each log in `$S`:
1. `git rev-parse HEAD > "$S/commit"`. A dirty tree stops the build.
2. `just build-shell`. It installs `chat-shell`, `chat-shell-host`, and each module that they need, from this commit.
3. `mvn -o -B -ntp -f chat-deploy/pom.xml -Pdeploy,shell,shell-host,client-backend -Dmaven.test.skip=true compile dependency:build-classpath -Dmdep.outputFile="$S/shell.cp"`.
4. For each `com.demo` jar in `shell.cp`, compare it with `<module>/target/<module>-0.0.1.jar` by SHA-256. Write `artifacts.tsv` with the module, both hashes, and `match` or `differ`.

Expected: exit 0, and every row of `artifacts.tsv` says `match`. A `differ` row stops the proof.

- [ ] **Step 4: Write `core.sh`, and start the core**

`start` refuses to run if 6990 or 6991 is busy. It writes a random service password to `$S/service.password` with mode `0600`. It starts this command in a new process group, inside the sandbox:

`perl -e 'setpgrp(0,0); exec @ARGV' sandbox-exec -f "$S/isolation.sb" env CORE_PORT=6990 CORE_MGMT_PORT=6991 CHAT_SERVICE_PASSWORD=<value> ./shell-scripts/chat-build core --memory --run --notls --node-id 0 --key-type uuid --init users,rootkeys`

It writes the group ID to `core.pgid`, and the output to `core.log`. It waits up to 120 s for `curl -s -u actuator:actuator http://127.0.0.1:6991/actuator/health` to hold `"status":"UP"`.

`stop` sends `TERM` to the group, waits up to 30 s for 6990 and 6991 to close, then sends `KILL` to the group. It succeeds only when both ports are closed and `pgrep -g <pgid>` finds no process.

Run: `core.sh start "$S"`, then `sandbox-exec -f "$S/isolation.sb" java docs/superpowers/proofs/2026-10-17-one-shot/Probe.java 6990 6991`
Expected: `start` exits 0, and both probe lines say `connected`.

- [ ] **Step 5: Write `launch.sh`, and check it with `help`**

The JVM options come from `CORE_PORT=6990 CORE_MGMT_PORT=6991 ./shell-scripts/chat-build shell --run --notls --node-id 1 --key-type uuid --dry-run`. Use each indented line that starts with `-D` or `--`, and drop `-Dspring-boot.run.*`. A variable `INTERACTIVE` (default `false`) sets `spring.shell.interactive.enabled`. The script ends with:

`exec sandbox-exec -f "$S/isolation.sb" env CORE_PORT=6990 CORE_MGMT_PORT=6991 java <options> -cp chat-deploy/target/classes:$(cat "$S/shell.cp") com.demo.chat.ChatAppKt "$@"`

Run: `docs/superpowers/proofs/2026-10-17-one-shot/launch.sh "$S" help </dev/null >"$S/t1.out" 2>"$S/t1.err"; echo $?`
Expected: any exit status. `t1.out` or `t1.err` shows a shell start. A `ClassNotFoundException` means that step 3 or this step is wrong.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/proofs/2026-10-17-one-shot/
git commit -m "CHAT-jmnjvupr: Add the stage 0 isolation, build, core, and launch scripts"
```

### Task 2: The case harness and the run

**Files:**
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/cases.sh`

**Interfaces:**
- Consumes: every script and file of task 1.
- Produces: `<scratch-dir>/results.tsv`, one row per case: `id`, `argv` with the password masked, `stdin`, `exit`, `wall_ms`, `stdout_bytes`, `stderr_bytes`, `timed_out`, `remote_ports`. Also `<scratch-dir>/<id>.out` and `<scratch-dir>/<id>.err`.

- [ ] **Step 1: Write `cases.sh`**

Each case starts `launch.sh` in a new process group, with `perl -e 'setpgrp(0,0); exec @ARGV' launch.sh ...`, in the background, with the given standard input. The background process ID is the group ID. The harness measures `wall_ms` from the launch to the exit of that process.

At 60 s, a watchdog sends `TERM` to the group, waits 5 s, then sends `KILL` to the group, and sets `timed_out`. After every case, timed out or not, the harness checks that `pgrep -g <pgid>` finds no process and that 9101 has no listener. A failed check stops the harness with exit 4.

While a case runs, the harness polls `lsof -nP -a -p <pid> -iTCP -sTCP:ESTABLISHED` for each process that `pgrep -g <pgid>` names. It writes each remote port to `remote_ports`. This is evidence for Q6 alone. The sandbox is the protection.

| ID | Program arguments | Standard input | Answers |
|----|-------------------|----------------|---------|
| C1 | `help` | `/dev/null` | Q1, Q2, Q5 |
| C2 | `whoami` | `/dev/null` | Q1, Q3, Q5 |
| C3 | `login --username Admin --password <password>` | `/dev/null` | Q3 |
| C4 | `login --username Admin --password wrong` | `/dev/null` | Q3, failed login |
| C5 | `no-such-command` | `/dev/null` | Q3, unknown command |
| C6 | `add-topic --name proof-room` | `/dev/null` | Q3, a command that needs a login |
| C7 | `@<scratch-dir>/script.txt` | `/dev/null` | Q4 |
| C8 | `login --username Admin` | the password, then a newline | Q7 |
| C9 | `whoami`, with `INTERACTIVE=true` | `/dev/null` | Q8 |
| C10 | `show-topics` | `/dev/null` | Q6: `proof-room` appears if C6 or C7 wrote it |
| C11 | `whoami`, after `core.sh stop` | `/dev/null` | Q3, core not available |

`script.txt` holds three lines: the C3 login, `add-topic --name proof-room`, and `messages proof-room`. The `Admin` password of the memory core is the one in `shared-deploy-configuration/src/main/config/userinit.yml`.

A case that hits a denied port fails with `Operation not permitted` in its stream. The harness then stops the core and exits 3.

- [ ] **Step 2: Run the harness**

Run: `docs/superpowers/proofs/2026-10-17-one-shot/cases.sh "$S" >"$S/cases.log" 2>&1; echo $?`
Expected: exit 0, and `results.tsv` has 11 rows. Exit 3 means that a shell tried a denied port. Exit 4 means that a case left a process or a 9101 listener. For either one, do not run the harness again. Report it to the owner.

- [ ] **Step 3: Check the end state**

Run: `for p in 6990 6991 9101; do lsof -nP -iTCP:$p -sTCP:LISTEN; done`, then `pgrep -g $(cat "$S/core.pgid")`, then `docker ps --format '{{.Names}} {{.Status}}' > "$S/docker-after"` and `diff "$S/docker-before" "$S/docker-after"`.
Expected: no listener, no process in the core group, and no difference other than the uptime text. Node 16 did not restart.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/proofs/2026-10-17-one-shot/cases.sh
git commit -m "CHAT-jmnjvupr: Add the stage 0 case harness"
```

### Task 3: The proof record

**Files:**
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/RECORD.md`
- Modify: `docs/superpowers/specs/2026-10-17-topic-register-design.md`, the "Unverified" section only

**Interfaces:**
- Consumes: `results.tsv`, `artifacts.tsv`, `commit`, `denied-ports`, and the stream files.
- Produces: the record that the stage 1 plan reads.

- [ ] **Step 1: Write `RECORD.md`**

It holds:
- The commit, `java -version`, the date, the ports, and the denied ports.
- The isolation proof of task 1 step 2.
- The `artifacts.tsv` table.
- The `results.tsv` table, with the password masked.
- For each case, the first 10 lines of each stream that is not empty.
- One answer per question Q1 to Q8, each with the case IDs that show it.
- "Effect on stage 1": for each design part 1 to 5, "holds" or "changes", and what changes.

- [ ] **Step 2: Update the "Unverified" section of the design**

For items 1, 2, 3, and 6, add the measured result and a pointer to `RECORD.md`. Do not change an item that the proof did not measure.

- [ ] **Step 3: Check the prose and the diff**

Run: `grep -c ';' docs/superpowers/proofs/2026-10-17-one-shot/RECORD.md; git diff --check; drift check`
Expected: `0`, no output from `git diff --check`, and `drift check` passes.

- [ ] **Step 4: Commit, push, and log**

```bash
git add docs/superpowers/proofs/2026-10-17-one-shot/RECORD.md docs/superpowers/specs/2026-10-17-topic-register-design.md
git commit -m "CHAT-jmnjvupr: Record the stage 0 one-shot proof"
git push
```

Then comment on `CHAT-jmnjvupr` with the answers to Q1 to Q8. The next step is the stage 1 plan. It reads `RECORD.md` first.
