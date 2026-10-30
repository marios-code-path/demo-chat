# Node-9 Consul Container Address Implementation Plan

Issue: `CHAT-hdxiopde`

**Goal:** Make authserv reach Consul on the shared Docker network on Linux.

**Architecture:** Keep the host preflight on `CONSUL_HOST:CONSUL_PORT`.
Build the authserv image with the Docker service address `consul:8500` by default.
Allow explicit container address overrides for deployments with another Consul service name or port.

**Files:**

- `justfile`: Select the container Consul host and port before `chat-build`.
- `shell-scripts/test-authserv-profile.sh`: Exercise the recipe with command stubs.
- `shell-scripts/README-chat-build.md`: Explain the two address scopes.

## Task 1: Prove the host and container addresses stay separate

1. Extend `shell-scripts/test-authserv-profile.sh` with a temporary JWK and command stubs.
   The `curl` stub must answer the host core and Consul checks.
   The `docker` stub must pass Postgres readiness and record when authserv starts.
   The `conda` stub must record `CONSUL_HOST` and `CONSUL_PORT` for the image build.
2. Run the test with host `CONSUL_HOST=127.0.0.1` and `CONSUL_PORT=6892`.
   Expect the existing recipe to fail the new `consul:8500` assertion.
   This failure must occur before the implementation edit.
3. In `justfile`, replace the `host.docker.internal` rewrite with these defaults:

   ```bash
   container_consul_host="${AUTHSERV_CONTAINER_CONSUL_HOST:-consul}"
   container_consul_port="${AUTHSERV_CONTAINER_CONSUL_PORT:-8500}"
   ```

4. Pass both selected values to the image build:

   ```bash
   env CONDA_NO_PLUGINS=true CONSUL_HOST="$container_consul_host" \
       CONSUL_PORT="$container_consul_port" DOCKER_CONFIG="$build_docker_config" \
       conda run -n base \
       ./shell-scripts/chat-build authserv --build --notls --node-id 8 --consul \
       --profile jdbc --profile client-init ${key_args[@]+"${key_args[@]}"}
   ```

   Keep the existing `chat-build` arguments in this call.
5. Run the focused test again.
   Expect the default image address to be `consul:8500`.
   Expect the host preflight to remain `127.0.0.1:6892`.
6. Add an override case with `AUTHSERV_CONTAINER_CONSUL_HOST=registry.internal` and `AUTHSERV_CONTAINER_CONSUL_PORT=8501`.
   Expect the image address to use those values while the host preflight remains unchanged.
7. Run `bash shell-scripts/test-authserv-profile.sh` and `just --dry-run deploy-authserv /tmp/authserv.jwk --key-type uuid`.

## Task 2: Document and verify the deployment contract

1. In `shell-scripts/README-chat-build.md`, describe the host and container Consul addresses.
   State the default `consul:8500` image address and the two override variables.
   State that `CHAT_NETWORK` must contain the selected Consul service alias.
2. Run `drift refs` on both edited paths.
   Review any bound prose before changing provenance.
3. Run `bash shell-scripts/test-authserv-profile.sh`, `bash shell-scripts/test-key-type-required.sh`, and `drift check`.
4. Run `git diff --check` and inspect the complete diff.
5. Commit the scoped change with the issue ID.
6. Push `chat-hdxiopde` for review.

## Live gate

Deploy from the branch on node2 after review.
Check that authserv reaches `consul:8500` and becomes healthy on port 9001.
Check the Postgres Docker network attachment separately before a token request.
The offline suite cannot prove node2 network state.
