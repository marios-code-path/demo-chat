# CI Integration Test Execution

Extends B5 (`CHAT-uortzsbx`). PR #38 made CI run the default test phase.
This spec makes CI run the tests that the default phase excludes.

Status: draft for review, 2026-08-28.

## What #38 already fixed

The workflow now:

1. Runs `mvn -B clean test` instead of `mvn clean test-compile`.
2. Fires once per commit: `push` is restricted to master, and a concurrency group
   cancels superseded runs.
3. Uses a cache key with no undefined variable, and action pins at v4.

`docs/BUILD-HEALTH.md` records B5 as resolved by #38.

## Remaining Problem

A default Maven run excludes every test tagged `integration`. That is about 160
tests. See `docs/BUILD-HEALTH.md`, "What the default run no longer covers".
They cover cassandra, redis, xstream, index-cassandra, deploy tests, and
chat-shell. CI does not run these tests. Thus, a passing CI check says nothing
about container-backed tests.

The original blockers are gone. B1, B2, and B4 are done. The verifier reported a
passing `-Pintegration` reactor against Docker Engine 29.7.2 on 2026-08-23.
The `KNOWN_FAILING_INTEGRATION` list was empty and measured.

That measurement predates one change. PR #51 (2026-08-28) made `app.nodeid`
explicit, required, and validated. The test image does not set it:
`chat-deploy-memory-integration-test/pom.xml` has no `-Dapp.nodeid` in
`BPE_APPEND_JAVA_TOOL_OPTIONS`. The image activates the memory key. Its
`KeyGenConfiguration` imports `NodeIdConfiguration`. Startup fails without the
value. Task 0 of the plan fixes the launch flags before any test run. Treat the
2026-08-23 image-backed test result as stale until Task 1 measures it again.

## Mechanism

One fact drives the design. `chat-shell` consumes a container image. The build
does not create that image by default:

- `chat-deploy-memory-integration-test` builds the image only under `-Ptest-build`.
- The image build runs in the `package` phase.
- Thus, `mvn test -Pintegration` cannot pass chat-shell in a clean environment.
  It stops before `package`, and the image does not exist.
- `mvn verify -Ptest-build,integration` works. The reactor builds the integration
  module first. `pom.xml` lists it at line 83, and chat-shell at line 84.
  The image exists before chat-shell tests run.

The CI job must use the second command. The local verifier cannot be copied
directly. It assumes that the local Docker daemon already has the image.

## Design

Add a second job to `.github/workflows/maven.yml`. Keep the existing job
unchanged.

```yaml
  integration:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
    # same checkout, setup-java, cache steps as the test job
    - name: Integration tests
      run: mvn -B clean verify -Ptest-build,integration
```

Details and reasons:

1. **Triggers: every pull request and every master push.** This finds container
   regressions before merge. The unit job stays fast.
2. **Informational, not a merge gate.** Master has no branch protection, so no
   check gates anything today. Do not add protection in this change. A failed
   check is visible and does not block.
3. **No `continue-on-error`.** The job must report failure as failure. It is
   informational because nothing is protected.
4. **`timeout-minutes: 30`.** Container suites can hang instead of fail. A hang
   with no timeout uses runner minutes and can look like a queued job.
5. **Same cache key as the unit job.** Both jobs restore the same `~/.m2` cache.
   On a PR, the two jobs start together. Neither job reliably populates the cache
   for the other. Accept the duplicate first-run download.
6. **Shared setup stays duplicated, not extracted.** Two jobs of eight steps are
   clearer than one reusable workflow. GitHub workflow files do not support YAML
   anchors.
7. **The job runs unit tests again inside `verify`.** This is deliberate. One
   reactor run matches the local verifier. It keeps image creation, dependency
   modules, and container tests in one order. The repeated unit phase costs about
   3 minutes. Do not change it to `-pl` scoping. The image build needs its
   dependency modules in the same reactor.

## Verification

Verification is part of the change. It is not after the change.

The issue names the criterion. A test job that has never failed is
indistinguishable from one that does not run. No CI run has failed since #38
merged. Neither job has proved that it can fail. The integration job is new, so
it needs the proof twice.

Two checks, before and during review:

**Local clean-image check (before pushing).** Delete the image from a running
Docker daemon, then run the exact CI command from the repo root:

```bash
docker rmi docker.io/library/chat-deploy-long-memory-integration-test:0.0.1
mvn -B clean verify -Ptest-build,integration
```

This proves the ordering claim. The image module is at `pom.xml` line 83.
chat-shell is at line 84. Maven keeps declaration order because chat-shell does
not depend on the image module. A local `~/.m2` cache is acceptable. The image
must not exist in the Docker daemon before the run.

**Failure proof (on the feature PR, never merged to master).** The issue names
the criterion. A test job that has never failed is indistinguishable from one
that does not run. A `pull_request` run uses the workflow file from the PR head.
A separate throwaway branch cannot exercise the new job before merge. The proof
therefore rides the same PR:

1. Push a commit that breaks one unit test (in the default reactor) and one test
   tagged `integration`.
2. Confirm that the unit job fails. Confirm that the integration job fails.
   Confirm one run per commit. Confirm that the integration log shows container
   tests. chat-shell reports about 19 tests running, not 36 with 17 skipped.
3. Push the revert, confirm both jobs pass, then merge. The failed commit never
   reaches master.

## Documentation

1. `docs/BUILD-HEALTH.md`: extend the B5 row. State that CI also runs
   `-Ptest-build,integration`. State that CI checks container tests for each
   commit.
2. Comment the outcome, the flakiness baseline, and the merge-gate decision on
   `CHAT-uortzsbx`, then close it.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where does the integration job run? | Every PR and master push | Find container regressions before merge |
| Does it gate merges? | No. Revisit after 10 runs | No flakiness record exists yet |
| How far does failure verification go? | Both jobs, on the feature PR. The failed commit is never merged | The PR head carries the workflow that the proof exercises |

## Out of scope

- Branch protection and required checks. Revisit after 10 integration runs.
- Nightly scheduled runs. The per-PR trigger covers the detection need.
- Docker API version on GitHub runners. The pinned plugin (3.5.12) negotiates
  instead of hardcoding v1.24. Thus, runner lag is not a failure mode. If a first
  run disagrees, the run log will show it.
- B1/B2/B4. All done.
