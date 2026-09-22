# Build Surface

This file is the entry point for repo-level build and run commands.

## Layers

Use `just` as the human command menu.

Use Maven as the build system.

Use `shell-scripts/` for implementation scripts and advanced launch details.

Do not start in `shell-scripts/` unless you need a script detail.

This file is the command reference. `docs/DEPLOYMENT-WORKFLOW.md` is the order
those commands go in, from a fresh machine to a running deployment, and it
names every input that has no default.

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

Use `./shell-scripts/build-health.sh --ci` when a change reaches the wire format or the server image.

`--integration` stops at the test phase, so it does not rebuild the `chat-shell` test image.

`--ci` reaches the package phase, so it builds the image before the container tests.

`--ci` takes the phase and the profiles of the CI integration job, and it resolves artifacts online. It is not the CI command. It adds `-fae`, because the verifier must see the result of every module.

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

### The authorization server needs a signing key

`app.oauth2.jwk.path` has no default, and this repository commits no key.

`chat-build authserv --run` requires `--jwk PATH`.

`AuthorizationServerConfig` reads an ES256 key in JWK form from that path. The
context does not start without it.

Give `--jwk` an absolute path. `spring-boot:run` sets the module directory as
the working directory, so a relative path would resolve against that rather
than against you.

### Make the key with gen-dckeys.sh

```bash
./shell-scripts/gen-dckeys.sh <cert-password>
./shell-scripts/chat-build authserv --run --notls --node-id 8 \
  --jwk "$PWD/encrypt-keys/server_keycert.jwk"
```

That script writes `encrypt-keys/server_keycert.jwk` with the private `d` and
an `x5c` chain. It is the same file that
`devops/k8s/volumes/make-cert-secrets.sh` puts in a Kubernetes secret, and
that `docker_volume_gen` copies to `/etc/keys`, so a local run and a deployed
one use one artifact.

`encrypt-keys` is in `.gitignore`. **Keep the key there.** A key under a
tracked directory is one `git add` away from a commit.

Measured on 2026-09-22: the authorization server started with that file and
minted a token whose header reads `{"alg":"ES256"}`. The `/oauth2/jwks`
endpoint answered with the `x5c` chain and **no `d`**, because Spring's
`NimbusJwkSetEndpointFilter` publishes `JWKSet.toString()`, which is
`toJSONObject(publicKeysOnly=true)`.

### Or make only a key, with no TLS material

Use this when you want a signing key without an authority and two identities.

```bash
NIMBUS=$(find ~/.m2/repository/com/nimbusds/nimbus-jose-jwt -name '*.jar' \
  | grep -v sources | sort | tail -1)
printf 'var jwk = new com.nimbusds.jose.jwk.gen.ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).keyID(java.util.UUID.randomUUID().toString()).generate();\njava.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/authserv.jwk"), jwk.toJSONString());\n/exit\n' > /tmp/genjwk.jsh
jshell --class-path "$NIMBUS" /tmp/genjwk.jsh
```

This key carries no `x5c` chain, and the authorization server does not need
one.

The tests generate their own. `AuthorizationServerTestSigningKey` makes an EC
P-256 key per run into a temporary file, so no test reads the file above. See
the B4 row in `docs/BUILD-HEALTH.md` for why the committed fixture went away.

**Do not commit a key.** A committed signing key would make every deployment
share one identity, which is the failure that `app.nodeid` already records.

`chat-build authserv --build` refuses `--jwk`. An image bakes the launch
options, and a path on your machine does not exist inside a container. Supply
`app.oauth2.jwk.path` when you run the image.

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
./shell-scripts/build-health.sh --ci
./shell-scripts/test-flags.sh
./shell-scripts/chat-build core --memory --run --notls --node-id 0 --init users,rootkeys
```
