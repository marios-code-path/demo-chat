# Authserv Container Deployment Plan

## Goal

Build authserv with `chat-build` and run it in Docker Compose.

## Design

Compose starts authserv and Postgres on the core network.
It mounts the JWK file read-only and sets its container path at runtime.
The launcher checks core health and its passing RSocket Consul registration.

## Work

### Compose

- [x] Require database and authserv credentials.
- [x] Mount the JWK file at `/run/secrets/authserv.jwk` as read-only.
- [x] Run authserv with the JWK owner's UID and the current GID.
- [x] Publish ports 9000 and 9001 on loopback.
- [x] Attach both containers to `${CHAT_NETWORK:-docker_chatnet}`.
- [x] Validate the Compose service definitions and JWK mount.

### Launcher

- [x] Keep checks for credentials, JWK ownership, core health, and authserv health.
- [x] Require a passing `rsocket` registration for `core-service-rsocket` in Consul.
- [x] Start Postgres and wait for database readiness.
- [x] Install reactor dependencies without running Maven tests.
- [x] Build authserv through `chat-build` with a temporary Docker config when needed.
- [x] Start authserv and wait for HTTP 200 from its management health endpoint.
- [x] Print each startup phase and progress every ten seconds during waits.

### Core discovery

- [x] Load `server-rsocket-consul.yml` from the RSocket feature for every backend.
- [x] Confirm the Cassandra dry run includes the RSocket Consul config.
- [x] Confirm the launcher checks the required Consul registration before the build.

### Documentation

- [x] Document the JWK command, required environment variables, and container behavior.
- [x] Record the Consul registration requirement and startup progress.

## Verification

- [x] Validate Compose configuration with placeholder credentials.
- [x] Check Just syntax and run its dry run.
- [x] Check Cassandra build arguments with `chat-build --dry-run`.
- [x] Run `drift check` and `git diff --check`.
- [x] Do not write or run tests, as requested.
