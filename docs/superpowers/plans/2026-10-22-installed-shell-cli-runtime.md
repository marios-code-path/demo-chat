# Installed Shell CLI Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans`. Work inline. `AGENTS.md` forbids subagent-driven development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** An installed node-9 home runs `chat-shell-cli` with no Git checkout, no Maven, and no `~/.m2`.

**Architecture:** `node-9/install` builds the jar set on demand and copies it to `<prefix>/lib/`. One launcher serves both trees: it reads the classpath from `lib/` when that directory exists, and from the source tree otherwise. The install freezes the JVM options into `lib/shell-cli.options`, because the source-tree launcher builds those options by calling `shell-scripts/chat-build`, which no installed copy holds.

**Tech Stack:** Bash 3.2, Maven, Java 25, the existing `chat-shell-cli` supervisor.

**Spec:** `docs/superpowers/specs/2026-10-21-portable-register-import-design.md`, lines 124 to 137. Issue `CHAT-bjqzvsoe`. Mario decided three points on 2026-10-22: build the jars on demand during the install, hold them under `<prefix>/lib/`, and fail with a clear error when Java is absent. Mario also recorded the exception to the `AGENTS.md` GraalVM rule, so a JVM launcher is permitted here. `CHAT-ulrkvfit` keeps the native profile gap.

## Global Constraints

- Theta is the development identity for the installed `whoami` acceptance gate. The gate requires status `0`, identity `Theta`, and empty standard error.
- The launcher selects the login at run time from `CHAT_SHELL_USER` and `CHAT_SHELL_PASSWORD_FILE`. The launcher, jar set, and frozen options hold no fixed login identity.
- A production operator selects a production identity and its credential separately. The Theta proof does not select that identity.
- Java floor is 25. `pom.xml` sets `<java.version>25</java.version>`.
- The frozen options serve key type `uuid` and node ID `9` alone.
- A run-time path never names the repository, `~/.m2`, or Maven.
- The install replaces `lib/` whole. It never touches `data/`, `secrets/`, or `images/`.
- No shipped script, no shipped code, and no shipped document names a
  development identity as a production value. The names `Sigma`, `Theta`,
  `Phi`, and `Mario` appear in test fixtures and proof records alone.
- The install writes the `.installed` marker last, after the jar set is in
  place. A marker means a complete home.
- A failed build leaves the previous home unchanged. The install builds into a
  temporary stage before it replaces anything.
- The measured jar set holds about 149 files and about 63 MB.
- Code is Bash 3.2. No associative array, and no `mapfile`.
- The main class is `com.demo.chat.ShellCliKt`.
- New prose follows `asd-ste100` strict mode. Commit messages name `CHAT-bjqzvsoe` and end with the attribution line.

## Review Focus

1. A host with no `java`. The install stops with status `2`, names `java`, and leaves no partial home. (Task 3)
2. A `java` older than 25. The install stops with status `2` and names the version that it found. (Task 3)
3. A second install after a dependency changes. `lib/` holds no file of the earlier set, and `data/`, `secrets/`, and `images/` keep every file. (Task 3)
4. An installed caller that asks for key type `long` or another node ID. The launcher refuses, because the options are frozen for `uuid` and node 9. (Task 1)
5. A stalled `register-read` through the installed launcher. The 30000 ms limit still holds, and the status is still `124`. (Task 1)
6. A second caller with another login. The launcher takes the identity and the
   password file at run time, so two callers reach two logins. No shipped file
   holds a login name. (Task 1)
7. A build that fails on a host that already holds an installed home. The
   previous home keeps its code, its jar set, and its marker. (Task 3)

## File Structure

- Modify `shell-scripts/chat-shell-cli`. One launcher, two classpath sources. It reads frozen options when they exist.
- Create `shell-scripts/build-shell-cli-lib`. It builds the jar set and stages it in one directory.
- Create `shell-scripts/test-shell-cli-lib.sh`. Offline cases for the launcher and the stage script.
- Modify `node-9/install`. Java check, Maven check, build on demand, `lib/` replacement, new dry-run lines.
- Modify `shell-scripts/test-node-9.sh`. Install cases for the new behavior.
- Modify `justfile`. One target that wraps the stage script.
- Modify `node-9/README.md` and `docs/SHELL-COMMAND-CONTRACT.md`.
- Create `docs/superpowers/proofs/2026-10-22-installed-shell-cli/RECORD.md` in Task 4.

---

### Task 1: One launcher, two classpath sources

**Files:**
- Modify: `shell-scripts/chat-shell-cli:140-186`
- Test: `shell-scripts/test-shell-cli-lib.sh`

**Interfaces:**
- Produces: the launcher reads `$CHAT_SHELL_LIB`, or `<launcher dir>/../lib` when that directory holds `shell-cli.options`. It then uses `-cp "<lib>/*"` and the options of that file, one option per line. It keeps the source-tree path when no such directory exists.
- Produces: the launcher refuses a frozen mismatch. `shell-cli.options` carries two header lines, `# key-type uuid` and `# node-id 9`. A caller with another value gets one error line and status `2`.
- Produces: the launcher passes `CHAT_SHELL_USER` and `CHAT_SHELL_PASSWORD_FILE` to Java at run time. It does not set either value to Theta.
- Consumes: `CHAT_SHELL_CLI_PRINT_OPTIONS=1`, which already prints the options and exits.

- [ ] **Step 1: Write the failing cases** in `shell-scripts/test-shell-cli-lib.sh`. Each case builds a fake lib directory with one empty jar, a `shell-cli.options` file, and a `java` stub on `PATH` that prints its own arguments.
  - `lib_mode_uses_the_lib_classpath`: the printed `-cp` names `<lib>/*` and names no `target/classes` path.
  - `lib_mode_uses_the_frozen_options`: every option line of the file reaches the command line.
  - `lib_mode_calls_no_chat_build`: a `chat-build` stub on `PATH` records no call.
  - `lib_mode_refuses_another_key_type`: `CHAT_SHELL_KEY_TYPE=long` gives status `2` and one line that names `uuid`.
  - `lib_mode_refuses_another_node_id`: `CHAT_SHELL_NODE_ID=1` gives status `2` and one line that names `9`.
  - `lib_mode_keeps_runtime_identity`: two calls with different `CHAT_SHELL_USER` values reach the Java stub with their respective password-file paths.
  - `source_mode_keeps_the_old_classpath`: with no lib directory, the printed `-cp` still names `chat-deploy/target/classes`.
  - `lib_mode_keeps_the_read_limit`: `register-read` with a `java` stub that sleeps gives status `124` and one line on standard error.
  - `lib_mode_passes_two_identities`: two runs, one with `CHAT_SHELL_USER=alpha` and `CHAT_SHELL_PASSWORD_FILE=<alpha file>`, one with `beta` and its own file. The `java` stub records the pair of each run. Each run carries its own identity and its own file, and neither run carries the other.
  - `lib_mode_holds_no_fixed_identity`: a search of the launcher and of `shell-cli.options` finds no development identity name.
- [ ] **Step 2: Run** `bash shell-scripts/test-shell-cli-lib.sh`. Expected: FAIL. The launcher has no lib mode.
- [ ] **Step 3: Implement** the lib branch in `shell-scripts/chat-shell-cli`. Resolve the lib directory before the option block. Read the two header lines and compare them with `CHAT_SHELL_KEY_TYPE` and `CHAT_SHELL_NODE_ID`. Skip the `chat-build` call in lib mode. Keep the supervisor and the limit table unchanged.
- [ ] **Step 4: Run** the suite. Expected: PASS.
- [ ] **Step 5: Run** `bash shell-scripts/test-node-9-register.sh`. Expected: 84 checks, 0 failures. That suite drives the launcher through `register`.
- [ ] **Step 6: Commit** the launcher and the new suite.

### Task 2: Build and stage the jar set

**Files:**
- Create: `shell-scripts/build-shell-cli-lib`
- Modify: `justfile:110-118`
- Test: `shell-scripts/test-shell-cli-lib.sh`

**Interfaces:**
- Produces: `build-shell-cli-lib <stage-dir>` builds the runtime set and writes `<stage-dir>/*.jar` plus `<stage-dir>/shell-cli.options`. It returns `2` for a missing argument, an existing non-empty stage directory, an absent `mvn`, or an absent `java`.
- Produces: the options file holds `# key-type uuid`, `# node-id 9`, and one JVM option on each later line. The script takes those lines from `CHAT_SHELL_CLI_PRINT_OPTIONS=1`.
- Consumes: `mvn -f chat-deploy/pom.xml -Pdeploy,shell,shell-host,client-backend -Dmaven.test.skip=true package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=<stage-dir>`, and the chat-deploy jar of that build.

- [ ] **Step 1: Write the failing cases** in the same suite, with a `mvn` stub that writes two fake jars and a `java` stub.
  - `stage_refuses_a_missing_argument`: status `2`.
  - `stage_refuses_an_existing_stage`: a non-empty directory gives status `2` and leaves it unchanged.
  - `stage_refuses_an_absent_mvn`: an empty `PATH` entry for `mvn` gives status `2` and one line that names `mvn`.
  - `stage_writes_the_options_header`: the first two lines are `# key-type uuid` and `# node-id 9`.
  - `stage_names_its_jar_count`: the last output line holds the jar count and the byte total.
  - `stage_writes_no_identity`: a search of the staged `shell-cli.options` finds no development identity name. The frozen options carry JVM options alone.
- [ ] **Step 2: Run** the suite. Expected: FAIL. The script is absent.
- [ ] **Step 3: Implement** `shell-scripts/build-shell-cli-lib`. Build into a temporary directory beside the stage, then rename, so a failed build leaves no partial stage. Copy the chat-deploy jar of the build beside the dependencies.
- [ ] **Step 4: Verify the real build once.** Run `bash shell-scripts/build-shell-cli-lib /tmp/cli-lib-probe`. Expected: status 0, about 149 jars, about 63 MB, and an options file whose header holds `uuid` and `9`. Record the real count and size in the commit message. Remove the probe directory.
- [ ] **Step 5: Add** the `just build-shell-cli-lib <dir>` target that calls the script.
- [ ] **Step 6: Run** the suite. Expected: PASS. **Commit.**

### Task 3: Install the runtime on demand

**Files:**
- Modify: `node-9/install:28-60`, `node-9/install:98-130`
- Test: `shell-scripts/test-shell-cli-lib.sh`

The new cases live in the CLI suite, not in `test-node-9.sh`. That suite needs
no free port, so these cases need no stop window for node 9. A full
`test-node-9.sh` run still needs ports 6890, 6891, and 6892, and Task 3 Step 6
asks Mario for that window once.

**Interfaces:**
- Consumes: `shell-scripts/build-shell-cli-lib` from Task 2.
- Produces: `install` runs in this order. 1. Check the arguments. 2. Check
  `java`, its major version, and `mvn`. 3. Build the jar set into a temporary
  stage beside the prefix. 4. Replace the code files and the code directories.
  5. Replace `lib/` with the stage by one rename. 6. Create the state
  directories. 7. Import the state when `--import-state` is present. 8. Write
  the `.installed` marker last.
- Produces: a failure of any step before step 4 leaves the prefix unchanged.
  So a failed build on a host with an installed home keeps that home whole,
  with its marker and its earlier jar set.
- Produces: `--dry-run` prints two more lines, `java: <version>` and
  `lib: <stage plan>`. It builds nothing.
- Produces: `--skip-cli` installs no `lib/`. It writes `cli: skipped` in the
  marker, so a reader of the marker can tell that home from a complete one.
  The offline suite uses the flag, because a real build takes minutes.

- [ ] **Step 1: Write the failing cases** in `shell-scripts/test-shell-cli-lib.sh`. Each case installs into a temporary prefix, with stubs for `java` and `mvn` on `PATH`.
  - `install_refuses_an_absent_java`: an empty `PATH` gives status `2`, one line that names `java`, and no `.installed` marker.
  - `install_refuses_an_old_java`: a `java` stub that prints version 21 gives status `2` and one line that names 21 and 25.
  - `install_builds_the_cli_lib`: a `mvn` stub writes two jars. The home then holds `lib/one.jar` and `lib/shell-cli.options`.
  - `install_replaces_the_lib_whole`: a stale file in `lib/` is gone after a second install, and `data/`, `secrets/`, and `images/` keep their files.
  - `install_dry_run_builds_nothing`: `--dry-run` prints a `lib:` line, and the `mvn` stub records no call.
  - `install_skip_cli_installs_no_lib`: `--skip-cli` leaves no `lib/` directory, writes the marker, and the marker holds `cli: skipped`.
  - `install_writes_no_marker_without_a_lib`: a `mvn` stub that exits 1 on a new prefix gives a nonzero status, no `lib/`, and no `.installed` marker.
  - `install_keeps_the_previous_home_when_the_build_fails`: a complete home, then a second install whose `mvn` stub exits 1. The code files, `lib/`, and the marker all keep their earlier content.
  - `install_holds_no_development_identity`: a search of `node-9/install`, `node-9/bin/`, and the installed `lib/shell-cli.options` finds no development identity name.
- [ ] **Step 2: Run** `bash shell-scripts/test-shell-cli-lib.sh`. Expected: FAIL on the nine new cases. This suite needs no free port.
- [ ] **Step 3: Implement** the step order of the Interfaces block in `node-9/install`: the checks, the build into a temporary stage, the code replacement, the `lib/` rename, the state directories, the import, and the marker last. Read the major version from `java -version` output. Treat `lib` as a code directory, not a state directory.
- [ ] **Step 4: Run** the suite. Expected: PASS.
- [ ] **Step 5: Commit.**
- [ ] **Step 6: Run the whole node-9 suite once.** Ask Mario to stop node 9, then run `bash shell-scripts/test-node-9.sh`. Expected: every case passes. Record the result. Ask Mario to start node 9 again.

### Task 4: The live acceptance proof

**Files:**
- Create: `docs/superpowers/proofs/2026-10-22-installed-shell-cli/RECORD.md`

- [ ] **Step 1: Install for real.** Mario runs `node-9/install`. Expected: status 0, and `/opt/demo-chat/node-9/lib` holds the jar set and the options file.
- [ ] **Step 2: Prove the development gate.** Run `CHAT_SHELL_USER=Theta CHAT_SHELL_PASSWORD_FILE=/opt/demo-chat/node-9/secrets/Theta.password /opt/demo-chat/node-9/bin/chat-shell-cli whoami`.
  Expect status `0`. Expect identity `Theta`. Expect empty standard error.
  This proof does not set the production login identity.
- [ ] **Step 3: Prove the isolation.** Run the same `whoami` check with `HOME=/nonexistent` and no Maven on `PATH`.
  Expect status `0`, identity `Theta`, and empty standard error.
- [ ] **Step 4: Write** `RECORD.md` with the commands and verbatim output.
  Record the jar count, byte total, and Java version. Include no password.
  State that Theta is a development identity of this proof.
- [ ] **Step 5: Defer the register read.** Stage 4A Task 10 creates `register-index` during import. It owns the later live register read. A read before import can fail because the index does not exist.
- [ ] **Step 6: Commit.** Then set `CHAT-bjqzvsoe` to done and comment the result on `CHAT-jmnjvupr`, because Task 10 of the import plan waits for this gate.

### Task 5: Documentation

**Files:**
- Modify: `node-9/README.md`, `docs/SHELL-COMMAND-CONTRACT.md`

- [ ] **Step 1:** Add a README section "The installed shell CLI". Name `lib/`, the frozen options, the Java 25 floor, the `--skip-cli` flag, and the rule that the options serve key type `uuid` and node 9 alone. Describe the login as two run-time values, `CHAT_SHELL_USER` and `CHAT_SHELL_PASSWORD_FILE`. Name no identity in that section.
- [ ] **Step 2:** Record Mario's exception in `docs/SHELL-COMMAND-CONTRACT.md`, beside the `CHAT-emwhnlay` sentence. Name `CHAT-bjqzvsoe` as the second permitted JVM command, and keep `CHAT-ulrkvfit` as the native gap.
- [ ] **Step 3:** Run `drift check` and `git diff --check <base>...HEAD`. Expected: no finding. Update prose before any `drift link`.
- [ ] **Step 4: Commit.**

## Self-Review

- **Spec coverage:** the runtime with no checkout, no Maven, and no `~/.m2` (Tasks 1 to 3). The `whoami` gate (Task 4). The GraalVM exception (Task 5, and the Spec section above). The build on demand, the `lib/` location, and the Java failure (Task 3, from Mario's three decisions). The run-time login and the identity rule (Task 1, Task 2, Task 3, and Task 5).
- **Revisions of 2026-10-22, after Mario's reading:** the login is a run-time
  pair, and no shipped file holds an identity. One case drives two identities.
  The install step order is explicit, so a failed build leaves the previous
  home whole. The marker goes last, and `--skip-cli` records itself in the
  marker. The new install cases moved to the port-free CLI suite, so they need
  no stop window. One later step asks for a stop window once, for a whole
  node-9 suite run.
- **Gap marked:** the source-tree launcher builds its JVM options by calling `shell-scripts/chat-build`. No installed copy holds that script. Task 1 and Task 2 freeze the options at install time instead. That is the one design decision this plan adds beyond Mario's three.
- **Calls I made:** the options file name and its two header lines, the `--skip-cli` flag for the offline suite, and the stage-then-rename order of the build.
- **Not covered by offline tests:** the real Maven build, the real JVM start, and the live login. Task 4 covers all three, and it needs Mario, because the install writes to a shared path.
