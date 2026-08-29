# CI Integration Test Execution Plan

> **For agents:** Implement this plan inline, task by task. This repository
> forbids subagent-driven development. See `CLAUDE.md`.

**Goal:** Make CI run the container tests. Prove that both CI jobs fail when a
test fails.

**Architecture:** No production code. One workflow file, one doc, one fp record.
The design and its verified mechanism live in the spec.

**Tech Stack:** GitHub Actions, `ubuntu-latest` (Docker daemon included), Maven,
JDK 17, Spring Boot build-image (plugin pinned 3.5.12 for the image module).

**Spec:** `docs/superpowers/specs/2026-08-28-ci-integration-execution-design.md`

**Issues:** tasks are fp subissues of `CHAT-uortzsbx`.

## Global Constraints

- Prose uses plain controlled English. Short sentences. Active voice. No
  semicolons.
- Do not add branch protection or required checks. The job is informational.
- Do not use `continue-on-error`. A failed job must show as failed.
- The unit job (`mvn -B clean test`) stays unchanged.
- `KNOWN_FAILING_INTEGRATION` in `shell-scripts/build-health.sh` stays empty.
- Check drift bindings on edited docs with `drift refs`. `drift check` must pass.

---

### Task 0 - Node id for the test image, branch start (`CHAT-lxsimrkl`)

Reviewer blocker, confirmed against source on 2026-08-28. The image is built
without `-Dapp.nodeid` (`chat-deploy-memory-integration-test/pom.xml:54`). The
image activates the memory key (`app.service.core.key=memory`).
`KeyGenConfiguration` imports `NodeIdConfiguration`. Thus, `NodeId.parse` fails
the container at startup. PR #51 made the property required after the
2026-08-23 passing measurement. Without this fix, Task 1 fails for the wrong
reason.

- [ ] Commit the spec and plan docs on master. They are untracked today.
      branch work must not leave them behind.
- [ ] Branch `ci-integration-execution` off master.
- [ ] Add `-Dapp.nodeid=900` to `BPE_APPEND_JAVA_TOOL_OPTIONS` in that pom.
- [ ] Comment it: 900 is this test deployment value. The image receives it at
      build time. It is not a shared YAML default. The memory backend has no
      claim store, so no lease check applies.
- [ ] Commit on the branch.

Done when: the pom contains the flag and its comment, committed on the branch.
Master has no `app.nodeid` in that pom until this task lands.

### Task 1 - Local clean-image check (`CHAT-cndmlnjn`)

Prove the CI command before the CI uses it. Needs Task 0: the image will not boot
without the node id.

- [ ] `docker rmi docker.io/library/chat-deploy-long-memory-integration-test:0.0.1 || true`
      "No such image" is success. The image must be absent before the run.
- [ ] `mvn -B clean verify -Ptest-build,integration`
- [ ] Confirm BUILD SUCCESS. Confirm the image rebuilds in the run. Confirm
      chat-shell reports about 19 tests run, not 36 with 17 skipped.
- [ ] `fp comment CHAT-cndmlnjn` with the tail of the reactor summary and the
      wall time. Set the issue done.

If this fails, stop. The ordering claim in the spec is wrong. Revise the job
design before you edit the workflow.

### Task 2 - Add the integration job, open the PR (`CHAT-vndtryfh`)

On the Task 0 branch.

- [ ] Add the job to `.github/workflows/maven.yml`:

```yaml
  integration:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
    - name: Checkout repository
      uses: actions/checkout@v4
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Cache Maven packages
      uses: actions/cache@v4
      with:
        path: |
          ~/.m2/repository
          ~/.m2/wrapper
        key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
        restore-keys: |
          ${{ runner.os }}-maven-
          ${{ runner.os }}-
    - name: Integration tests
      run: mvn -B clean verify -Ptest-build,integration
```

- [ ] Commit, push, open PR.
- [ ] Watch both jobs (`gh pr checks --watch`). Record run times in an fp
      comment. This is the first real run of the new job.

### Task 3 - Failure proof, then revert (`CHAT-zljzaiox`)

The workflow version a `pull_request` run uses comes from the PR head. The proof
must ride the same PR.

Reactor position is load-bearing. `verify` runs without `-fae`. Thus, a broken
test in an early module stops the integration job before any container starts.
Place the failures where the job must run container tests before it fails.

- [ ] Integration break: add one failing `@Test` to `LongUserCommandsTests` in
      `chat-shell/src/test/kotlin/com/demo/chat/test/init/ShellUserCommandsTests.kt`.
      The class carries `@Tag("integration")`. Thus, the unit job never loads it,
      and the daemon does not start there. Surefire runs sibling container
      classes in the same module before the module fails. The job shows
      container execution, then failure.
- [ ] Unit break: flip one assertion in
      `chat-authorization-server/src/test/kotlin/com/demo/chat/ClientInitializerTest.kt`.
      That module sits at `pom.xml` line 85, after `chat-shell` at line 84. The
      integration job therefore fails in chat-shell. It never reaches this
      break. The unit job excludes chat-shell tagged tests and fails here. Each
      job proves its own execution type.
- [ ] Commit both, message marked `B5-RED-PROOF` for searchability.
- [ ] Push. Let both jobs finish. Do not push again. The concurrency group
      cancels superseded runs.
- [ ] Confirm that the unit job fails. Confirm that the integration job fails.
      Confirm one run per commit. Confirm that the integration log shows
      container tests before the failure.
- [ ] Revert the commit. Push. Watch both jobs pass again.
- [ ] Record run IDs and outcome in an fp comment. Set the issue done.

### Task 4 - BUILD-HEALTH doc (`CHAT-tdfjpmed`)

- [ ] Extend the B5 row: CI also runs
      `mvn -B clean verify -Ptest-build,integration`.
- [ ] Update the "what the default run no longer covers" note. CI now checks
      container tests for each commit. It stays informational until 10 runs are
      recorded.
- [ ] `drift refs docs/BUILD-HEALTH.md`, then `drift check`.
- [ ] Commit on the same branch.

### Task 5 - Merge and record (`CHAT-oitsmdwe`)

- [ ] Merge after the fail-then-pass sequence is recorded.
- [ ] `fp issue assign CHAT-uortzsbx --rev <commits>`.
- [ ] `fp comment CHAT-uortzsbx`: outcome, PR number, both job run times,
      merge-gate decision, and the 10-run flakiness baseline note.
- [ ] `fp issue update --status done CHAT-uortzsbx`.
- [ ] Refresh `forward-register.md` on master: landed row, Last merged PR
      fields. `drift check`.

## Follow-Up

- After 10 integration runs: read the flakiness record, then decide required
  check versus status quo. That decision needs run data this plan produces.
