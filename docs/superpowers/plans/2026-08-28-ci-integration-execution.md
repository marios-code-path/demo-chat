# CI integration test execution Implementation Plan

> **For agentic workers:** Implement this plan inline, task by task. This
> repository forbids subagent-driven development. See `CLAUDE.md`.

**Goal:** Make CI run the container-backed half of the test suite, and prove — by
making it fail on purpose — that both CI jobs report red when a test fails.

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
- Do not use `continue-on-error`. Red must show as red.
- The unit job (`mvn -B clean test`) stays unchanged.
- `KNOWN_FAILING_INTEGRATION` in `shell-scripts/build-health.sh` stays empty.
- Check drift bindings on edited docs with `drift refs`. `drift check` must pass.

---

### Task 0 — Node id for the test image, branch start (`CHAT-lxsimrkl`)

Reviewer blocker, confirmed against source on 2026-08-28. The image is built
without `-Dapp.nodeid` (`chat-deploy-memory-integration-test/pom.xml:54`). The
image activates the memory key (`app.service.core.key=memory`), and
`KeyGenConfiguration` imports `NodeIdConfiguration`, so `NodeId.parse` fails the
container at startup. PR #51 made the property required after the 2026-08-23
green measurement. Without this fix, Task 1 fails for the wrong reason.

- [ ] Commit the spec and plan docs on master. They are untracked today, and
      branch work must not leave them behind.
- [ ] Branch `ci-integration-execution` off master.
- [ ] Add `-Dapp.nodeid=900` to `BPE_APPEND_JAVA_TOOL_OPTIONS` in that pom.
- [ ] Comment it: 900 is this test deployment's own value, set in the image
      at build time. It is not a shared YAML default. The memory backend
      enforces no cross-deployment claim, so no collision check applies.
- [ ] Commit on the branch.

Done when: the pom contains the flag and its comment, committed on the branch.
Master has no `app.nodeid` in that pom until this task lands.

### Task 1 — Local zero-to-one check (`CHAT-cndmlnjn`)

Prove the CI command before the CI uses it. Needs Task 0: the image will not boot
without the node id.

- [ ] `docker rmi docker.io/library/chat-deploy-long-memory-integration-test:0.0.1`
      — "No such image" is success. The point is the image is absent before the
      run. `docker rmi ... || true` is the honest form.
- [ ] `mvn -B clean verify -Ptest-build,integration`
- [ ] Confirm BUILD SUCCESS. Confirm the image rebuilds in the run. Confirm
      chat-shell reports about 19 tests run, not 36 with 17 skipped.
- [ ] `fp comment CHAT-cndmlnjn` with the tail of the reactor summary and the
      wall time. Set the issue done.

If this fails, stop. The ordering claim in the spec is wrong, and the job design
needs revising before any workflow is touched.

### Task 2 — Add the integration job, open the PR (`CHAT-vndtryfh`)

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

### Task 3 — Red proof, then revert (`CHAT-zljzaiox`)

The workflow version a `pull_request` run uses comes from the PR head. The proof
must ride the same PR.

Reactor position is load-bearing. `verify` runs without `-fae`, so a broken test
in an early module stops the integration job before any container starts. The
failures go where the job must execute container tests before failing.

- [ ] Integration break: add one failing `@Test` to `LongUserCommandsTests` in
      `chat-shell/src/test/kotlin/com/demo/chat/test/init/ShellUserCommandsTests.kt`.
      The class carries `@Tag("integration")`, so the unit job never loads it and
      the daemon never starts there. Surefire runs the sibling container classes
      in the same module before the module fails, so the job shows container
      execution, then red.
- [ ] Unit break: flip one assertion in
      `chat-authorization-server/src/test/kotlin/com/demo/chat/ClientInitializerTest.kt`.
      That module sits at `pom.xml` line 85, after `chat-shell` at line 84. The
      integration job therefore fails in chat-shell and never reaches this break.
      The unit job excludes chat-shell's tagged tests and fails here. Each job
      proves its own kind of execution.
- [ ] Commit both, message marked `B5-RED-PROOF` for searchability.
- [ ] Push. Let both jobs finish. Do not push over them — the concurrency group
      cancels superseded runs.
- [ ] Confirm: unit job red, integration job red, one run per commit. Confirm
      the integration log shows container tests executing before the red.
- [ ] Revert the commit. Push. Watch both jobs green again.
- [ ] Record run IDs and outcome in an fp comment. Set the issue done.

### Task 4 — BUILD-HEALTH doc (`CHAT-tdfjpmed`)

- [ ] Extend the B5 row: CI also runs
      `mvn -B clean verify -Ptest-build,integration`.
- [ ] Update the "what the default run no longer covers" note: the container
      half is now checked per commit, informational until 10 runs are recorded.
- [ ] `drift refs docs/BUILD-HEALTH.md`, then `drift check`.
- [ ] Commit on the same branch.

### Task 5 — Merge and record (`CHAT-oitsmdwe`)

- [ ] Merge after the red-then-green sequence is recorded.
- [ ] `fp issue assign CHAT-uortzsbx --rev <commits>`.
- [ ] `fp comment CHAT-uortzsbx`: outcome, PR number, both job run times,
      gating decision, and the 10-run flakiness baseline note.
- [ ] `fp issue update --status done CHAT-uortzsbx`.
- [ ] Refresh `forward-register.md` on master: landed row, Last merged PR
      fields. `drift check`.

## Follow-up, not in this chain

- After 10 integration runs: read the flakiness record, then decide required
  check versus status quo. That decision needs run data this plan produces.
