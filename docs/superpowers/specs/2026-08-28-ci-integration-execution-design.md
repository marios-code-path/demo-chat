# CI Integration Test Execution

Extends B5 (`CHAT-uortzsbx`). PR #38 made CI run the default test phase. This spec
makes CI run the tests the default phase excludes.

Status: draft for review, 2026-08-28.

## What #38 already fixed

The workflow now:

1. Runs `mvn -B clean test` instead of `mvn clean test-compile`.
2. Fires once per commit: `push` is restricted to master, and a concurrency group
   cancels superseded runs.
3. Uses a cache key with no undefined variable, and action pins at v4.

`docs/BUILD-HEALTH.md` records B5 as resolved by #38.

## The remaining problem

A default Maven run excludes every test tagged `integration`. That is about 160
tests (`docs/BUILD-HEALTH.md`, "What the default run no longer covers"). They cover
cassandra, redis, xstream, index-cassandra, the deploy tests, and chat-shell. CI
does not run any of them. The container-backed half of the suite therefore has the
same property B3 had: nothing checks it between commits, and a green check says
nothing about it.

The original blockers are gone. B1, B2 and B4 are all done. The verifier reports
the full `-Pintegration` reactor green against Docker Engine 29.7.2 on 2026-08-23,
and its `KNOWN_FAILING_INTEGRATION` list is empty and measured.

That measurement predates one change. PR #51 (2026-08-28) made `app.nodeid`
explicit, required, and validated. The test image does not set it:
`chat-deploy-memory-integration-test/pom.xml` bakes no `-Dapp.nodeid` into
`BPE_APPEND_JAVA_TOOL_OPTIONS`, while the image activates the memory key, whose
`KeyGenConfiguration` imports `NodeIdConfiguration` and fails startup without the
value. Task 0 of the plan fixes the launch flags before anything runs. The
2026-08-23 green claim for the image-backed path is treated as stale until the
Task 1 re-measurement.

## Mechanism

One fact drives the design. `chat-shell` consumes a container image that the build
does not produce by default:

- `chat-deploy-memory-integration-test` builds the image only under `-Ptest-build`.
- The image build runs in the `package` phase.
- So `mvn test -Pintegration` cannot pass chat-shell in a clean environment. It
  stops before `package`, and the image is never built.
- `mvn verify -Ptest-build,integration` works. The reactor builds the integration
  module first (`pom.xml` lists it at line 83, chat-shell at line 84), so the image
  exists before chat-shell's tests run.

The CI job must use the second form. The local verifier cannot be copied directly,
because it assumes the image already exists in the local Docker daemon.

## Design

Add a second job to `.github/workflows/maven.yml`. Keep the existing job unchanged.

```yaml
  integration:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
    # same checkout, setup-java, cache steps as the test job
    - name: Integration tests
      run: mvn -B clean verify -Ptest-build,integration
```

Details, with reasons:

1. **Triggers: every pull request and every master push.** This catches container
   regressions before merge. The unit job stays fast, so this does not slow the
   quick signal.
2. **Informational, not a merge gate.** Master has no branch protection, so no
   check gates anything today. Do not add protection in this change. A red X is
   visible and does not block. That is the correct posture for a suite with no
   CI flakiness history.
3. **No `continue-on-error`.** The job must report red honestly. It is
   informational because nothing is protected, not because it forces success.
4. **`timeout-minutes: 30`.** Container suites can hang instead of fail. A hang
   with no timeout burns runner minutes and looks like a queued job.
5. **Same cache key as the unit job.** Both restore the same `~/.m2` cache. On a
   PR, the two jobs start at once, so neither reliably populates the cache for the
   other. Accept the duplicate first-run download.
6. **Shared setup stays duplicated, not extracted.** Two jobs of eight steps are
   clearer than one reusable workflow. GitHub does not support YAML anchors in
   workflow files.
7. **The job re-runs the unit tests inside `verify`.** This is deliberate. One
   reactor invocation mirrors the local verifier and keeps the image build, the
   dependency modules and the container tests in one ordering. The repeated unit
   phase costs about 3 minutes. Do not "fix" it with `-pl` scoping: the image
   build needs its dependency modules in the same reactor.

## Verification (part of the change, not after it)

The issue names the criterion: a test job that has never failed is indistinguishable
from one that does not run. No CI run has failed since #38 merged. Neither job has
ever proved it can go red. The integration job is new, so it needs the proof twice.

Two checks, before and during review:

**Local zero-to-one check (before pushing).** Delete the image from a running
Docker daemon, then run the exact CI command from the repo root:

```bash
docker rmi docker.io/library/chat-deploy-long-memory-integration-test:0.0.1
mvn -B clean verify -Ptest-build,integration
```

This proves the ordering claim (image module at `pom.xml` line 83, chat-shell at
line 84, and Maven keeps declaration order because chat-shell does not depend on
the image module). A local `~/.m2` cache is acceptable. The image must not exist
in the Docker daemon beforehand.

**Red proof (on the feature PR, never merged to master).** The issue names the
criterion: a test job that has never failed is indistinguishable from one that
does not run. A `pull_request` run uses the workflow file from the PR head, so a
separate throwaway branch cannot exercise the new job until it is merged. The
proof therefore rides the same PR:

1. Push a commit that breaks one unit test (in the default reactor) and one test
   tagged `integration`.
2. Confirm: unit job red, integration job red, one run per commit, and the
   integration report shows container tests executing. chat-shell reports about
   19 tests running, not 36 with 17 skipped.
3. Push the revert, confirm both jobs green, then merge. The red commit never
   reaches master.

## Documentation

1. `docs/BUILD-HEALTH.md`: extend the B5 row to say CI also runs
   `-Ptest-build,integration`, and that the container half of the suite is now
   checked per commit.
2. Comment the outcome, the flakiness baseline, and the gating decision on
   `CHAT-uortzsbx`, then close it.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where does the integration job run? | Every PR and master push | Catch container regressions before merge |
| Does it gate merges? | No. Revisit after 10 runs | No flakiness record exists yet, and a flaky gate trains people to ignore checks |
| How far does red verification go? | Both jobs, on the feature PR. The red commit is never merged | The new job gets the same proof the old one lacked, and the PR head carries the workflow the proof exercises |

## Out of scope

- Branch protection and required checks. Revisit after 10 integration runs.
- Nightly scheduled runs. The per-PR trigger already covers the detection need.
- Docker API version on GitHub runners. The pinned plugin (3.5.12) negotiates
  instead of hardcoding v1.24, so runner lag is not a failure mode. If a first run
  disagrees, the run log will say so.
- B1/B2/B4. All done.
