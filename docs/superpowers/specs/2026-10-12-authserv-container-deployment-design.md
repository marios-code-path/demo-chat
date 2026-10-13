# Authserv Container Deployment Design

## Goal

The `deploy-authserv` recipe builds and starts the authorization server as a Docker container.
It keeps the current environment checks and core health check.
It also requires a passing Consul registration tagged `rsocket` for core.

## Design

The recipe builds the image with `chat-build authserv --build`.
It does not pass the JWK path to the image build.
The image must read the JWK at container runtime.

The Compose file in `chat-authorization-server` starts Postgres and authserv.
Both services join the external `${CHAT_NETWORK:-docker_chatnet}` network.
The authserv container mounts the JWK file at a fixed, read-only path.
The container sets `app.oauth2.jwk.path` to that path.
The container runs with the JWK owner UID and the current user's GID.
The recipe checks that the current user owns the JWK file.
The datasource URL uses the Compose service name `postgres`.

The image uses Consul discovery.
The build sets `CONSUL_HOST=host.docker.internal`, as the core container build does.
The authserv container maps `host.docker.internal` to the Docker host.
The shared Docker network lets authserv reach the registered core container.

The recipe starts Postgres, waits for database readiness, builds the image, and starts authserv.
It then checks the mapped management health endpoint.
It prints its current phase and sends a progress update every ten seconds while it waits.
The recipe requires healthy core and a passing RSocket Consul registration.
It keeps the current password and database environment variables.

## Scope

This change does not add multiple authserv instances or a stop command.
It does not add tests, as the user requested no tests for this work.

## Verification

Check the shell syntax, Compose configuration, resolved `chat-build` arguments, and drift status.
Do not run tests.
