# REST Token Relay to Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task.

**Goal:** Relay the validated REST agent identity to core RSocket calls and validate that identity at the core boundary.

**Architecture:** Put JWT properties, decoder creation, claim checks, and bearer authentication in `chat-security`. Use one core RSocket authentication manager for simple and bearer metadata. Encode core authentication and authorization refusals as a versioned typed envelope. Decode that envelope in the REST client and map its typed kind to HTTP 401 or 403.

**Tech Stack:** Kotlin, Spring Boot, Spring Security RSocket, Spring Security OAuth2 resource server, Reactor, Maven, Bash.

## File map

- `chat-security/src/main/kotlin/com/demo/chat/config/agent/` will hold shared properties, decoder, claim validation, and bearer authentication types.
- `chat-service-controller/src/main/kotlin/com/demo/chat/config/rsocket/` will hold the composite RSocket authentication manager and the typed security error interceptor.
- `chat-client-rsocket/src/main/kotlin/com/demo/chat/config/client/rsocket/` will attach request bearer metadata and decode typed core security errors.
- `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/` will use the shared validation types and retain REST route configuration.
- `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/` will hold core refusal, identity precedence, and credential preservation tests.
- `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/` will hold downstream effect tests.
- `chat-deploy-memory-integration-test/src/test/kotlin/com/demo/chat/deploy/test/` will hold the opt-in two-process deployment test.
- `chat-client-rsocket/src/test/kotlin/com/demo/chat/test/rsocket/` will hold request-context and error-decoder tests.
- `chat-service-controller/src/test/kotlin/com/demo/chat/test/controller/` will hold core authentication and error-envelope tests.
- `shell-scripts/chat-build`, `shell-scripts/test-flags.sh`, and `shell-scripts/golden/` will hold the core agent launch contract.
- `docs/BUILD.md` and `forward-register.md` will document the launch and transport contract.

## Task 1: Establish the red tests and shared configuration contract

- [x] Add `AgentSecurityProperties` tests for the four values: client id, username, required scope, and JWK path.
- [x] Add a core binding test that starts with no agent values and confirms bearer validation is disabled.
- [x] Add a core binding test that sets only the client id and expects startup failure naming `app.security.jwt.jwk-path`.
- [x] Add the matching partial-value case for a JWK path without a client id.
- [x] Add a REST binding test that requires all four values when the REST resource server is enabled.
- [x] Add `BearerAuthenticationNotConfiguredManager` tests that reject bearer authentication without a configured JWK.
- [x] Add tests for the shared client id and required scope validators.
- [x] Run the focused binding tests and record the pre-implementation failures before the implementation.

## Task 2: Move shared JWT validation into `chat-security`

- [x] Move the agent properties and ES256 decoder factory from `chat-webflux` into `chat-security`.
- [x] Move the client id and scope validation into `chat-security`.
- [x] Use the shared `AgentIdentityLifecycle` with the REST composite user client.
- [x] Add the resource server dependencies required by the shared decoder and bearer manager.
- [x] Make the core binding optional when all agent values are absent.
- [x] Make partial core configuration fail during binding with the missing property name.
- [x] Keep REST startup failure for missing agent configuration.
- [x] Run the binding tests and the focused module tests.

## Task 3: Add core composite authentication and refusal ordering

- [x] Implement `RSocketAuthenticationManager` in `chat-service-controller`.
- [x] Route simple authentication to the existing password manager.
- [x] Route `BearerTokenAuthenticationToken` to the shared JWT manager.
- [x] Use `BearerAuthenticationNotConfiguredManager` when the core has no JWK.
- [x] Pass the composite manager to the existing `simpleAuthentication` configuration.
- [x] Attach the security interceptor through the conditional `RSocketServerCustomizer`.
- [x] Confirm the bearer branch runs before `AnonymousPayloadInterceptor`.
- [x] Add `CoreBearerWithoutJwkTests` for a validly shaped bearer request against a core with no JWK.
- [x] Add `BearerDenialNoDownstreamEffectsTests` for an expired token and a wrong client id.
- [x] Verify that neither request reaches the controller, service, store, index, secrets, or pubsub seams.
- [x] Add `RSocketIdentityPrecedenceTests` for setup identity, request identity, missing request metadata, and invalid request metadata.
- [x] Add `RSocketServiceCredentialPreservedTests` for the existing Service credential path.
- [x] Run the focused and full `chat-deploy-memory` tests.

## Task 4: Relay request bearer metadata without changing shell behavior

- [x] Add a request metadata provider that reads `AgentAuthenticationToken` from the reactive security context at request subscription time.
- [x] Encode a valid token with `BearerTokenAuthenticationEncoder` and `BearerTokenMetadata`.
- [x] Attach bearer metadata to each delegated REST request.
- [x] Attach no bearer metadata when the context has no `AgentAuthenticationToken`.
- [x] Preserve the existing Service credential provider and its simple metadata format.
- [x] Add `RestBearerRelayContextTests` for bearer attachment, no bearer attachment, and per-request context lookup.
- [x] Run the focused client tests and the existing shell seam tests.

## Task 5: Add typed core security errors and REST status mapping

- [x] Add `RSocketSecurityErrorPayload` with a version and a typed `kind` field for authentication and authorization refusals.
- [x] Add `RSocketSecurityErrorPayloadInterceptor` around the core security chain. It catches authentication and authorization exceptions and serializes the typed envelope.
- [x] Keep the envelope as transport data. Do not classify errors by message text.
- [x] Add `CoreSecurityErrorDecoder` that parses the structured envelope from `ApplicationErrorException` and rejects malformed or unknown envelopes.
- [x] Add typed client errors for core authentication refusal and core authorization denial.
- [x] Map the decoded core authentication refusal to HTTP 401 in the REST facade.
- [x] Map the decoded core authorization refusal to HTTP 403 in the REST facade.
- [x] Use the typed envelope kind to distinguish the two errors. Do not match exception message text.
- [x] Add `RestCoreAuthenticationErrorMappingTests` for both statuses and for an expired token that fails at core.
- [x] Add tests that assert no mapping path returns HTTP 500 for either typed refusal.
- [x] Run the focused controller, client, and web tests.

## Task 6: Add the real two-process REST to core test

- [x] Add `RestToCoreBearerDeploymentTests` under `chat-deploy-memory-integration-test`.
- [x] Generate a temporary ES256 test key and configure the same JWK, client id, username, and scope in both processes.
- [x] Start the core and REST applications as separate JVM processes on loopback ports.
- [x] Wait for both processes to answer their health or readiness endpoint before sending the test request.
- [x] Mint a valid test token with the configured client id and scope.
- [x] Send the token to the REST facade and call a route that reaches core authorization.
- [x] Read the stored room owner row through an independent Service connection. Compare its principal key with the configured Agent key.
- [x] Assert that the REST response proves the authorization decision used that identity.
- [x] Stop both processes in a `finally` block and include their logs when startup or request handling fails.
- [x] Run this test with reactor-built executable jars through the `rest-core-e2e` profile.
- [x] Keep this test out of the default build. Enable it with the `rest-core-e2e` profile.

## Task 7: Extend the build and documentation contracts

- [x] Allow agent flags for `rest` and `core` in `shell-scripts/chat-build`.
- [x] Continue rejecting agent flags for `authserv`, `gateway`, and `shell`.
- [x] Require an absolute existing JWK path when agent flags are present.
- [x] Add the core agent case to `shell-scripts/test-flags.sh`.
- [x] Update its golden output and review the diff.
- [x] Update `docs/BUILD.md` with matching core and REST launch commands.
- [x] Document that a core without agent values starts with bearer validation disabled.
- [x] Document that partial agent values fail startup and name the missing property.
- [x] Document the standard RSocket bearer metadata and the setup versus request precedence.
- [x] Update `forward-register.md` with the shared validation module, typed refusal envelope, and the issuer and audience dependency boundary.
- [x] Run the shell flag test and review the documentation changes.

## Task 8: Full verification and review

- [x] Run the full deployment-memory reactor test.
- [x] Run the two-process deployment test with the `rest-core-e2e` profile.
- [x] Run `shell-scripts/build-health.sh --ci` with an empty `DOCKER_CONFIG`.
- The earlier local `build-health.sh --ci` run reported 1894 tests, 0 failures, 0 errors, and 65 skipped tests.
- That run preceded the final property guard, actuator policy, Redis exclusions, and profile repairs.
- The `chat-shell` module reports 77 tests, 0 failures, 0 errors, and 27 skipped tests.
- The rebuilt image is `sha256:279913975476175fc1a83865d705e7c514a6853b4cd595084286998cbefe05e6`.
- [x] Inspect the complete diff for scope, generated files, secrets, and stale documentation.
- [x] Run `git status --short --branch`, `git diff --check`, and `drift check`.
- [x] Confirm that issuer and audience validation remains owned by `CHAT-okpgpxkj`, which depends on `CHAT-mpjtnpqv`.
The owner requested no commit and no pull request during this repair.

## Repair verification

The condition test failed for empty and invalid auth values before the repair.
It passes after all composite auth configurations require `true`.
The core refusal test covers expired and wrong-client tokens, with no controller or downstream calls.
The production context excludes Boot's security registration and has one security interceptor.

The following command passed with cached dependencies.
It rebuilt both executable jars in the same reactor before the two-process test.

```bash
mvn -o -B -pl chat-deploy-memory-integration-test -am verify \
  -Prest-core-e2e \
  -Dtest=RestToCoreBearerDeploymentTests -Dsurefire.failIfNoSpecifiedTests=false
```

The later full local build passed with the counts recorded above.
That full build rebuilt the shell image before running the shell tests.
All 19 launch-flag cases also passed.

## Final review verification

The property guard now requires the literal value `true`, even when an interceptor exists.
The new guard tests first reported five failures. All six cases now pass.

The decision is to protect `rootkeys` with actuator credentials.
The existing REST startup client already sends Basic credentials.
The deployment test first reported HTTP 200 where HTTP 401 was required.
Both deployment tests now pass through the profile without a separate system property.

All three Redis test applications exclude Boot's RSocket security auto-configuration.
The Redis boot test confirms one security interceptor and no Boot security configuration.

The final integration command was:

```bash
./shell-scripts/build-partial.sh --modules chat-deploy-redis,chat-shell --mode integration
```

The run used an empty Docker configuration and rebuilt the shell image.
It reports 1472 tests, zero failures, zero errors, and 66 skipped tests across 21 modules with tests.
The shell reports 77 tests, zero failures, zero errors, and 27 skipped tests.
The default build skips both deployment tests. Their separate opt-in run passes both tests.
The final image is `sha256:0f708618b9328ee217de18fb2bc3e1f926e4adbc605a31f7e48ec1fe308236a0`.
The full reactor ran again after these final repairs. `build-health.sh --ci` reports 1901 tests in 30 modules.
It reports 0 failures, 0 errors, 66 skipped tests, and no drift.
The shell reports 77 tests and 27 skipped tests.
That run rebuilt the shell image as `sha256:34dded7aba26cd2a5dc711e08784380bdef5f6a6e76c6679acbd73f4e9303e47`.
