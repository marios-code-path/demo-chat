# Topic register, stage 0: the one-shot proof run

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task. `AGENTS.md` forbids subagent-driven development, so do not use it. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run `chat-shell` once per command against a disposable core, with redirected streams, and record what it does. The stage 1 plan uses that record to fix its interface.

**Architecture:** A disposable memory core runs on host ports 6990 and 6991. A harness script starts the shell JVM directly, with no Maven process around it. It runs one case per process and records each stream, the exit status, and the time. A proof record states the results and their effect on stage 1. This stage changes no product code.

**Tech Stack:** Bash, Java 25, Spring Shell 4.0.3, the `chat-deploy` shell launch, the `chat-deploy-memory` core.

**Spec:** `docs/superpowers/specs/2026-10-17-topic-register-design.md`, sections "Build order" (stage 0), "chat-shell as a command tool", and "Unverified".

## Global Constraints

- **No effect on node 16.** Do not use `chat-deploy`, `docker compose`, or `just deploy-*`. Stop the disposable core by its process ID alone.
- Node 16 listens on 6790 and 6791. The disposable core uses `CORE_PORT=6990` and `CORE_MGMT_PORT=6991`.
- Key type `uuid`. Node id `0` for the disposable core.
- One Maven build per worktree. Log each Maven run to a file, and read only its exit status and summary lines.
- Proof files go in `docs/superpowers/proofs/2026-10-17-one-shot/`. Raw stream files go in the session scratchpad, not in git.
- Prose follows ASD-STE100 strict mode.
- Every merge needs the owner's named `--admin` bypass.

## Questions the proof answers

Each question maps to a part of the design. The proof record answers each one with a measured result.

| ID | Question | Design part |
|----|----------|-------------|
| Q1 | With `spring.shell.interactive.enabled=false`, does the shell run one command and then exit? | Unverified 1, part 3 |
| Q2 | What do standard output and standard error hold besides command output? | Part 1 |
| Q3 | What exit status does the process give for success, a failed command, an unknown command, and a failed login? | Part 2 |
| Q4 | Does a first argument `@<file>` run a script? | Part 2, the `@` refusal |
| Q5 | How long does one run take, from process start to exit? | Decision 20 |
| Q6 | Does `CORE_PORT=6990` send the RSocket connection to 6990? | Part 5, unverified 3 |
| Q7 | Does a command read the process standard input? | Unverified 6 |
| Q8 | What does the shell do today, in interactive mode, with redirected standard input? | Owner report of 2026-10-17 |

## Review Focus

1. **The shell reaches node 16 by mistake.** If `CORE_PORT` does not reach the RSocket client, the shell connects to 6790. A topic list cannot prove the target, because `show-topics` can fail before a login. So the harness watches the TCP connections of each shell process. A connection to port 6790 or 6791 stops the harness at once, before any later case.
2. **A case does not exit.** A one-shot process can stay alive because of the reactive web server on 9101 or a JLine terminal. Each case has a 60-second limit. The harness kills the process at the limit and records `timeout`.
3. **Port 9101 is busy.** Two shell processes at once collide on `server.port=9101`. The harness runs cases one at a time, and it checks that 9101 is free first.
4. **A stale core.** A core left from an earlier attempt gives wrong results. The harness refuses to start when 6990 or 6991 is busy.
5. **Secrets in the record.** The case commands hold the `Admin` password of the disposable core. The record shows `<password>` in place of it.

---

### Task 1: The disposable core and the shell launch

**Files:**
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/core.sh`
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/launch.sh`

**Interfaces:**
- Produces: `core.sh start <scratch-dir>` starts the core, waits for `http://127.0.0.1:6991/actuator/health` to answer `UP` within 120 s, writes the core PID to `<scratch-dir>/core.pid`, and exits 0. `core.sh stop <scratch-dir>` kills that PID and waits for 6990 to close.
- Produces: `launch.sh <scratch-dir> [shell-args...]` runs one shell JVM with the given program arguments. Standard input, output, and error pass through. It exits with the JVM exit status.
- Produces: `<scratch-dir>/shell.cp` holds the shell classpath, and `<scratch-dir>/shell.opts` holds one JVM option per line.
- The scratch directory is `S=/private/tmp/claude-501/-Users-darkbit1001-workspace-demo-chat/<session>/scratchpad/stage0`, or any new empty directory outside git. Every later step uses `$S`.

- [ ] **Step 1: Check the preconditions**

Run: `for p in 6990 6991 9101; do lsof -nP -iTCP:$p -sTCP:LISTEN; done; docker ps --format '{{.Names}} {{.Status}}'`
Expected: no listener on 6990, 6991, or 9101. Record the `docker ps` lines. Task 3 compares them.

- [ ] **Step 2: Write `core.sh`**

`start` runs `CORE_PORT=6990 CORE_MGMT_PORT=6991 CHAT_SERVICE_PASSWORD=<random> ./shell-scripts/chat-build core --memory --run --notls --node-id 0 --key-type uuid --init users,rootkeys` in the background, with output to `<scratch-dir>/core.log`. It writes the random service password to `<scratch-dir>/service.password` with mode `0600`. It refuses to start if 6990 or 6991 is busy.

- [ ] **Step 3: Start the core and check it**

Run: `docs/superpowers/proofs/2026-10-17-one-shot/core.sh start "$S"`, then `curl -s http://127.0.0.1:6991/actuator/health`
Expected: exit 0, and the health answer holds `"status":"UP"`. If the health check needs the actuator user, use `actuator:actuator`, the value of the shell plan.

- [ ] **Step 4: Write `launch.sh`**

Build the shell classes once with `mvn -o -B -ntp -f chat-deploy/pom.xml -Pdeploy,shell,shell-host,client-backend -Dmaven.test.skip=true compile`. Then write the classpath with `dependency:build-classpath -Dmdep.outputFile=<scratch-dir>/shell.cp` in the same profiles. Log both runs to files. Take the JVM options from `CORE_PORT=6990 CORE_MGMT_PORT=6991 ./shell-scripts/chat-build shell --run --notls --node-id 1 --key-type uuid --no-host-command --dry-run`. Use each indented line that starts with `-D` or `--`, and drop `-Dspring-boot.run.*`. The script runs `java <options> -cp chat-deploy/target/classes:<classpath> com.demo.chat.ChatAppKt "$@"` with `CORE_PORT=6990` and `CORE_MGMT_PORT=6991` in its environment. A variable `INTERACTIVE` (default `false`) sets the value of `spring.shell.interactive.enabled` in the options.

- [ ] **Step 5: Check the launch with the help command**

Run: `timeout 60 docs/superpowers/proofs/2026-10-17-one-shot/launch.sh "$S" help </dev/null >"$S/t1.out" 2>"$S/t1.err"; echo $?`
Expected: any exit status. The file `t1.out` or `t1.err` shows a shell start. A `ClassNotFoundException` or a missing main class means step 4 is wrong. Fix it before task 2.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/proofs/2026-10-17-one-shot/core.sh docs/superpowers/proofs/2026-10-17-one-shot/launch.sh
git commit -m "CHAT-jmnjvupr: Add the stage 0 core and shell launch scripts"
```

### Task 2: The case harness and the run

**Files:**
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/cases.sh`

**Interfaces:**
- Consumes: `core.sh`, `launch.sh`, and `<scratch-dir>/service.password` from task 1.
- Produces: `<scratch-dir>/results.tsv`, one row per case: `id`, `argv` with the password masked, `stdin`, `exit`, `wall_ms`, `stdout_bytes`, `stderr_bytes`, `timed_out`, `core_ports`. Also `<scratch-dir>/<id>.out` and `<scratch-dir>/<id>.err`.

- [ ] **Step 1: Write `cases.sh`**

Each case runs `launch.sh` once, under a 60-second limit (`timeout 60`), with the given standard input. It records the row above. It measures `wall_ms` from just before the launch to process exit.

**The connection watch.** While a case runs, the harness polls `lsof -nP -a -p <pid> -iTCP -sTCP:ESTABLISHED` every 0.2 s for the JVM and its children. It writes each remote port to `core_ports`. A remote port 6790 or 6791 means node 16. Then the harness kills the case, stops the disposable core, and exits 3.

The cases run in this order:

| ID | Program arguments | Standard input | Answers |
|----|-------------------|----------------|---------|
| G1 | `show-topics` | `/dev/null` | Review Focus 1: the read guard |
| C1 | `help` | `/dev/null` | Q1, Q2, Q5 |
| C2 | `whoami` | `/dev/null` | Q1, Q3, Q5 |
| C3 | `login --username Admin --password <password>` | `/dev/null` | Q3 |
| C4 | `login --username Admin --password wrong` | `/dev/null` | Q3, failed login |
| C5 | `no-such-command` | `/dev/null` | Q3, unknown command |
| C6 | `add-topic --name proof-room` | `/dev/null` | Q3, a command that needs a login |
| C7 | `@<scratch-dir>/script.txt` | `/dev/null` | Q4 |
| C8 | `whoami` | the text `hello` | Q7 |
| C9 | `whoami`, with `INTERACTIVE=true` | `/dev/null` | Q8 |
| C10 | `show-topics` | `/dev/null` | Q6: `proof-room` appears if C6 or C7 wrote it |
| C11 | `whoami`, after `core.sh stop` | `/dev/null` | Q3, core not available |

`script.txt` holds three lines: the C3 login, `add-topic --name proof-room`, and `messages proof-room`. The `Admin` password of the memory core is the one in `shared-deploy-configuration/src/main/config/userinit.yml`.

G1 runs first, so the connection watch tests the target before any write case.

- [ ] **Step 2: Run the harness**

Run: `docs/superpowers/proofs/2026-10-17-one-shot/cases.sh "$S" >"$S/cases.log" 2>&1; echo $?`
Expected: exit 0, and `results.tsv` has 12 rows. Exit 3 means that a shell connected to node 16. Do not run the harness again. Report it to the owner.

- [ ] **Step 3: Check Q6 and node 16**

Run: `cut -f1,10 "$S/results.tsv"`, then `docker ps --format '{{.Names}} {{.Status}}'`.
Expected: each `core_ports` value holds 6990 or 6991 alone, or is empty for a case that made no connection. The `docker ps` lines match task 1 step 1, and the node-16 uptime did not reset.

- [ ] **Step 4: Stop the core, and check that nothing is left**

Run: `core.sh stop "$S"` if C11 did not stop it, then `for p in 6990 6991 9101; do lsof -nP -iTCP:$p -sTCP:LISTEN; done`
Expected: no listener on the three ports.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/proofs/2026-10-17-one-shot/cases.sh
git commit -m "CHAT-jmnjvupr: Add the stage 0 case harness"
```

### Task 3: The proof record

**Files:**
- Create: `docs/superpowers/proofs/2026-10-17-one-shot/RECORD.md`
- Modify: `docs/superpowers/specs/2026-10-17-topic-register-design.md`, the "Unverified" section only

**Interfaces:**
- Consumes: `results.tsv` and the stream files from task 2.
- Produces: the record that the stage 1 plan reads.

- [ ] **Step 1: Write `RECORD.md`**

It holds:
- The commit, `java -version`, the date, and the ports.
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
