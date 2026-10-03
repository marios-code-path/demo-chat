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

### The authorization server needs the service password

**The core RSocket routes require `ROLE_SERVICE` or `ROLE_ADMIN` since
2026-10-02.** The authorization server reads the secrets store and the indexes
through those routes. So it sends the credential of the `Service` account on
every request. See `CHAT-rdlghoqe`.

Export one variable before you start the core and the authorization server:

    export CHAT_SERVICE_PASSWORD='<a password you choose>'

- The core reads it as the `Service` password in `userinit.yml`.
- The authorization server reads it as `app.client.rsocket.credential.password`.
  `chat-build authserv` emits `-Dapp.client.rsocket.credential.username=Service`.

**The authorization server refuses to start when the password is blank.** The
message names `app.client.rsocket.credential.password`.

**A core that starts with the variable unset generates the password.** It
prints one `Generated password for account 'Service':` line. Set
`CHAT_SERVICE_PASSWORD` to that value before you start the authorization
server.

A plain user cannot reach the core routes. The shell commands that call them
directly need an `Admin` login. See `docs/ANONYMOUS-AUTHORIZATION.md`.

## The agent token on a REST launch

A `rest` launch mounts the application chain. That chain requires a valid agent
token on every route it owns. A `core` launch can validate the same token at
the RSocket boundary.

Pass the four application values on the command line. The application
properties have no defaults.

    ./chat-build rest --run --notls --node-id 2 \
        --jwk /abs/path/server_keycert.jwk \
        --agent-client-id <client-id> --agent-username <handle>

Start the core with the same four values:

    ./chat-build core --memory --run --notls --node-id 1 \
        --jwk /abs/path/server_keycert.jwk \
        --agent-client-id <client-id> --agent-username <handle>

Both `--notls` and `--node-id` are required. Each launch exits 2 without
either one. Give each process its own node id.

Both launches emit `app.security.agent.client-id`,
`app.security.agent.username`, `app.security.agent.required-scope`, and
`app.security.jwt.jwk-path`. The core starts with bearer validation disabled
when all four values are absent. Partial values fail startup and name the
missing property.

An RSocket server requires `app.service.composite.auth=true`.
An absent, empty, or false value causes startup failure.
The security chain, auth services, and grant writers use the same condition.
The domain codec and key-verification handler remain independent of that condition.

The `rootkeys` actuator endpoint requires actuator Basic credentials.
The REST startup client already sends `actuator:actuator` through `HttpRootKeyConsumeOnStart`.
These are the current startup defaults. This repair does not add configurable startup credentials.
`CHAT-npqgshiu` tracks that existing limitation under the actuator-password issue, `CHAT-dmnhxnsp`.
Anonymous requests to `rootkeys` receive HTTP 401.

Run the optional two-process test from the repository root:

```bash
mvn -B -pl chat-deploy-memory-integration-test -am verify \
  -Prest-core-e2e -Dtest=RestToCoreBearerDeploymentTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The profile builds both executable jars before the test module and enables its deployment tests.
No separate `run.rest.core.e2e` property is required.
The default build skips these tests. CI does not run them.

`--agent-scope` defaults to `chat.mcp`.

Use `Agent` for `--agent-username`. `userinit.yml` declares that handle, and
startup creates the account. The deployment refuses to start unless that handle
answers exactly one user. The account is a plain user, so it holds no
administrator reach. See `CHAT-werokcbb` and `docs/MCP-CREDENTIAL-ISSUANCE.md`.

`chat-build` accepts these flags on the `rest` and `core` services. The REST
service is a facade over a core service, so a core must run first. It reads its
root keys over HTTP and holds no store.

`docs/MCP-CREDENTIAL-ISSUANCE.md` states that topology, and it names the
single-process form that the acceptance run used.

A core launch does not mount the HTTP chain. Its RSocket authentication manager
validates bearer metadata when the four values are present.

Give `--jwk` an absolute path. `spring-boot:run` sets the module directory as
the working directory, so a relative path would resolve against that rather
than against you.

### Get the token from the authorization server

A client obtains its token from the authorization server. It does not mint one.
Minting needs the private signing key, which belongs to the server alone.

    export CHAT_SERVICE_PASSWORD='<the Service password of the core>'
    ./chat-build authserv --run --notls --node-id 8 --profile memory \
      --jwk "$PWD/encrypt-keys/server_keycert.jwk"

    curl -sS -u '<client-id>:<client-secret>' \
      -d 'grant_type=client_credentials' -d 'scope=chat.mcp' \
      http://127.0.0.1:9000/oauth2/token

`--profile memory` selects the in-memory client repository. It reads
`app.oauth2.client` from `oauth2-client.yml`, which carries the `chat.mcp`
scope.

**The `jdbc` Maven profile does not select that repository.** It is a build
profile and it is always active for this service. The Spring profile alone
selects the repository.

The token lives 300 seconds. The audience equals the client id.

`docs/MCP-CREDENTIAL-ISSUANCE.md` carries the full procedure for the MCP
adapter, with the refusal matrix and the limits.

### Make the key with gen-dckeys.sh

```bash
./shell-scripts/gen-dckeys.sh <cert-password>
export CHAT_SERVICE_PASSWORD='<the Service password of the core>'
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
