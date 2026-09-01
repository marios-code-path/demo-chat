# Build Surface

This file is the entry point for repo-level build and run commands.

## Layers

Use `just` as the human command menu.

Use Maven as the build system.

Use `shell-scripts/` for implementation scripts and advanced launch details.

Do not start in `shell-scripts/` unless you need a script detail.

## First Commands

Show the command menu:

```bash
just --list
```

Check the default build state:

```bash
just check
```

Check the container test state:

```bash
just check-integration
```

Check `chat-build` flag output:

```bash
just check-flags
```

## Maven Commands

Run the same command as the CI build job:

```bash
just ci-local
```

This recipe runs:

```bash
mvn -B clean test
```

Run the same command as the CI integration job:

```bash
just ci-integration-local
```

This recipe runs:

```bash
mvn -B clean verify -Ptest-build,integration
```

The integration command needs Docker.

The integration command builds the test image before `chat-shell` uses it.

## Build Health

Use `docs/BUILD-HEALTH.md` for current build state.

Use `./shell-scripts/build-health.sh` when you need exact drift output.

Use `./shell-scripts/build-health.sh --integration` before merge-sensitive integration changes.

The CI integration job runs on pull requests and `master` pushes.

The integration job is informational until the 10-run baseline is complete.

## Local Launch

Use `chat-build` through `just` for common local launch commands.

Start the core service with memory storage:

```bash
just launch-memory 0
```

Start the interactive shell client against a running core service:

```bash
just launch-shell 1
```

Print a launch command without execution:

```bash
just dry-run-memory 0
just dry-run-shell 1
```

`app.nodeid` has no default.

`chat-build` requires `--node-id`.

Choose a node id that is unique for deployments that write to one Redis or Cassandra store.

See `docs/NODEID-CLAIM.md` for the node-id lease rules.

See `shell-scripts/README-chat-build.md` for all `chat-build` flags.

## Installed Artifacts

`chat-build` resolves library modules from the local Maven repository. The launch also uses the installed `chat-deploy` jar.

After a pull or a branch switch, refresh the installed artifacts once:

```bash
mvn clean install -DskipTests
```

The launch and image commands pass `-Dmaven.test.skip=true`. Stale test classes in the local repository cannot break a launch.

Use `mvn clean` whenever you build. A stale `target/` directory reports false results. See B6 in `docs/BUILD-HEALTH.md`.

## Direct Script Use

Use scripts directly when a recipe does not cover the task.

Common direct commands:

```bash
./shell-scripts/build-health.sh
./shell-scripts/build-health.sh --integration
./shell-scripts/test-flags.sh
./shell-scripts/chat-build core --memory --run --notls --node-id 0 --init users,rootkeys
```
