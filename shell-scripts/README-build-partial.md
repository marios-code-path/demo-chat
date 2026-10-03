# Partial builds

`build-partial.sh` runs the same command locally and in the manual GitHub workflow.
It builds selected modules and their required project dependencies with Maven `-pl` and `-am`.
It returns Maven's exit status.

## Local commands

```bash
just build-partial chat-security
just build-partial chat-security,chat-mcp
just build-partial chat-shell integration
just dry-run-partial chat-shell integration
just check-partial
```

The script also works without `just`:

```bash
./shell-scripts/build-partial.sh --modules chat-security
./shell-scripts/build-partial.sh --modules chat-shell --mode integration
./shell-scripts/build-partial.sh --modules chat-shell --mode integration --dry-run
```

Use module directory names from the root `pom.xml`.
Separate names with commas and omit spaces.
The script rejects unknown modules and invalid modes before Maven starts.
It runs from the repository root, regardless of the caller's directory.

Both modes require Maven and JDK 25. Integration mode also needs Docker for container tests.
Maven can download missing dependencies in both modes.

## Modes

| Mode | Maven phase and profiles | Scope |
|---|---|---|
| `unit` (default) | `clean verify` | Runs tests except those tagged `integration`. |
| `integration` | `clean verify -Pintegration` | Also runs tests tagged `integration`. |

Both modes reach `verify` because some modules consume test JARs created during `package`.
The build creates those JARs from the selected source and its project dependencies.

For shell integration tests, the script adds `chat-deploy-memory-integration-test` and enables `test-build`.
The root POM orders that module before `chat-shell`, so the build creates the image before shell tests start.
An explicit image-module selection in integration mode also enables `test-build`.

`-am` includes required dependencies. It does not select downstream consumers.
Select those consumers explicitly when the change needs their tests.
The existing automatic full CI workflow remains unchanged.

## GitHub commands

The default branch must contain `.github/workflows/maven-partial.yml` before GitHub accepts a manual dispatch.
See [GitHub's manual workflow instructions](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
The selected branch must also contain the workflow and scripts.
GitHub uses committed source from that branch. Local uncommitted changes are excluded.

In Actions, select **Maven partial build**.
Select **Run workflow**.
Choose the branch, modules, and mode.

Alternatively, replace `BRANCH` with the remote branch name:

```bash
gh workflow run maven-partial.yml --ref BRANCH \
  -f modules=chat-security,chat-mcp -f mode=unit
```

The workflow uploads the build log and test reports, including when Maven fails.
It calls the script directly, so the runner does not need `just` or Zsh.

`build-health.sh` continues to compare full builds with `docs/BUILD-HEALTH.md`.
Partial builds do not evaluate that full-build baseline.
`chat-build` continues to prepare deployment commands and service properties.
