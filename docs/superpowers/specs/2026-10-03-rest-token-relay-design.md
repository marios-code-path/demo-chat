# REST token relay to core RSocket

Issue `CHAT-mpjtnpqv`. Parent issue `CHAT-jkordfef`.

This document revises the design after owner review. The owner approved the
design after the tracker dependency correction.

## Problem

The REST resource server validates an agent bearer token at the HTTP boundary.
The REST client then calls core RSocket routes without caller metadata.
Core therefore sees the request as anonymous.

The fix must preserve the configured agent identity across the REST to core
boundary. An invalid relay must fail authentication before any handler or
downstream store runs.

## Build contract

`chat-build` currently rejects agent flags for every service except `rest`.
The core must receive the same trust and identity values.

The implementation will allow `--agent-client-id`, `--agent-username`, and
`--agent-scope` for `rest` and `core`.

The implementation will require `--jwk PATH` for either service when agent
flags are present.

The implementation will continue to reject agent flags for `authserv`,
`gateway`, and `shell`.

The change will update `shell-scripts/test-flags.sh` and its committed golden
files.

The change will add a core agent case that asserts the four `app.security.*`
values in the emitted flags.

`docs/BUILD.md` will show matching core and REST launch commands.

The core must start with the trusted public JWK and the same client id,
username, and scope values as the REST process.

## Shared validation module

The shared JWT validation will live in the `chat-security` Maven module.
The core does not load `chat-webflux`, so the core must not import its security
configuration.

`chat-security` will own these shared seams under `com.demo.chat.config.agent`:

- `AgentSecurityProperties` for the `app.security.agent` and `app.security.jwt`
  values.
- `AgentJwtDecoderFactory` for the ES256 decoder built from the public JWK.
- The bearer claim converter used by HTTP and RSocket.

`chat-webflux` will keep its HTTP filter chain. The shared
`AgentIdentityLifecycle` resolves the configured user through its composite
user client.

`chat-service-controller` will configure the RSocket authentication manager
from `chat-security`.

The RSocket configuration will resolve the configured agent username through
the core `ReactiveUserDetailsService`. It will create the same
`ChatUserDetails` principal that the core uses for a password login.

The shared manager will support both simple username and password metadata and
bearer metadata.

`chat-service-controller` will provide `RSocketAuthenticationManager` to
`simpleAuthentication`.

`RSocketAuthenticationManager` will route simple credentials to the existing
password manager and bearer credentials to the shared JWT manager.

When bearer validation is not configured, `BearerAuthenticationNotConfiguredManager`
will reject the bearer token with an authentication exception.

This explicit branch prevents Spring's simple-auth password manager from
interpreting bearer metadata as a password credential.

The authorization server Service credential will continue to use simple
authentication. It will not use the agent bearer path.

The core will bind agent security properties as an optional configuration.

A core with none of the agent properties starts with bearer validation disabled.

A core with any agent property starts only when the complete required set is
present: client id, username, scope, and JWK path.

A client id without a JWK path fails startup and names the missing property.

A JWK path without a client id fails startup and names the missing property.

The REST application requires the complete set whenever its application chain
is enabled.

## Bearer transport

The REST client will use the standard RSocket bearer metadata format.
It will encode `BearerTokenMetadata` with
`BearerTokenAuthenticationEncoder`.

The REST client will attach bearer metadata to every delegated request that
holds an `AgentAuthenticationToken`.

It will not place the REST bearer token in connection setup metadata.

The client wrapper will read the reactive security context when the request
is subscribed. It will not read a token during shared requester construction.

When the context has no `AgentAuthenticationToken`, it attaches no bearer
metadata. The configured simple credential remains available for service calls.

This preserves the caller identity when one requester serves many requests.

It also prevents one request identity from remaining on a shared connection.

## Authentication precedence

Request metadata has precedence over setup metadata.

If a request has valid authentication metadata, core uses that request
identity for the request.

If a request has no authentication metadata, core uses the authenticated setup
identity when one exists.

If a request carries invalid or expired authentication metadata, core refuses
the request. It does not use the setup identity and it does not use `Anon`.

If a connection has no setup identity and a request has no metadata, existing
anonymous behavior remains available for routes that permit it.

The test `RSocketIdentityPrecedenceTests` will cover setup identity,
request identity, absent request metadata, and invalid request metadata.

## Core without a configured JWK

A core with no configured `app.security.jwt.jwk-path` must reject bearer
metadata.

Spring Security's `AuthenticationPayloadInterceptor` invokes
`BearerAuthenticationNotConfiguredManager` for bearer metadata when the core
has no JWK.

The request must not continue to the anonymous interceptor.

The request must not reach a controller, a service, a store, an index, or
pubsub.

The test `CoreBearerWithoutJwkTests` will connect to a core without a JWK,
send validly shaped bearer metadata, and assert an authentication failure on
the first request.

The test will also assert that the request does not become anonymous.

`BearerAuthenticationNotConfiguredManager` will produce this refusal when the
core has no JWK configuration. `RSocketSecurityErrorPayloadInterceptor` wraps
the authentication exception in the typed transport envelope.

The refusal will occur before `AnonymousPayloadInterceptor` runs.

## REST error mapping

The REST facade will map a core bearer authentication refusal to HTTP 401.

The REST facade will map a valid caller's core authorization denial to HTTP 403.

Neither refusal will become HTTP 500.

`RestCoreAuthenticationErrorMappingTests` will assert both mappings through the
REST boundary.

The core `RSocketSecurityErrorPayloadInterceptor` will encode a versioned
`RSocketSecurityErrorPayload` with a typed `kind` field for authentication and
authorization refusals.

`CoreSecurityErrorDecoder` will decode that payload into a typed client error.

The REST mapper will select 401 or 403 from the decoded error kind.

It will not inspect exception message text.

An expired token can fail at core after the REST check. The REST response will
still be HTTP 401.

## Issuer and audience

Issuer and audience validation are moved from `CHAT-mpjtnpqv` to
`CHAT-okpgpxkj`.

`CHAT-okpgpxkj` will depend on `CHAT-mpjtnpqv` because this issue builds the
core bearer path that the issuer and audience checks extend.

The repository has not selected the expected values. Current evidence shows
that the access-token audience equals the client id.

`CHAT-okpgpxkj` owns measurement and the owner decision for the issuer and
audience contract.

`CHAT-okpgpxkj` will select the issuer and audience contract and add validation
to the shared `chat-security` path.

`CHAT-mpjtnpqv` will not claim issuer or audience acceptance after its work
merges.

## Denial effects

`BearerDenialNoDownstreamEffectsTests` checks the REST boundary in `chat-deploy`.
`CoreBearerDenialNoDownstreamEffectsTests` checks the core RSocket boundary in `chat-deploy-memory`.

It will verify that the controller, persistence, index, secrets, and pubsub
seams receive no call after invalid bearer authentication.

The test will cover an expired token and a token with a wrong client id.

`RSocketServiceCredentialPreservedTests` will prove that the Service credential
still reaches protected core routes.

`RestBearerRelayContextTests` will prove that no bearer metadata is attached
when the reactive context has no `AgentAuthenticationToken`.

The existing shell path will continue to use its login credential or no
credential according to its command state.

## End-to-end deployment test

`RestToCoreBearerDeploymentTests` in `chat-deploy-memory-integration-test` will start a core JVM and a REST JVM as two
separate processes.

The test will issue a valid agent token for the configured client and scope.

The test will send the token to the REST facade and call a route that reaches
core authorization.

The REST process resolves the configured username through its composite user
client. The core process resolves the same username through its local user
service.

The test will assert that room creation and removal succeed through core
authorization for the configured identity.

The test uses the shipped `Agent` account, which has no root wildcard grant.
An independent Service connection reads the stored room owner row and the configured Agent key.
The test compares the complete owner principal key with the Agent key before room removal.

## Verification

An RSocket server requires the literal property value `app.service.composite.auth=true` and a security interceptor.
A bare flag must fail startup even if another configuration supplies an interceptor.

The `rootkeys` actuator endpoint requires actuator credentials.
The REST startup client already sends Basic credentials through `HttpRootKeyConsumeOnStart`.
Anonymous access is unnecessary for startup and is rejected.
The current startup client uses the existing `actuator` username and password defaults.
Configurable startup credentials remain outside this repair.
`CHAT-npqgshiu` tracks the existing credential limitation under `CHAT-dmnhxnsp`.

Focused verification will cover `chat-security`, `chat-client-rsocket`,
`chat-service-controller`, `chat-webflux`, `chat-deploy-memory`, and
`chat-deploy`.

The two-process deployment test will run after the deploy modules build their executable jars.

The test is opt-in. The `rest-core-e2e` profile enables the test through Surefire.
No separate system property is required.

The shell flag contract will run through `shell-scripts/test-flags.sh`.

Documentation changes will run `drift check` and `git diff --check`.

A prior local `build-health.sh --ci` run passed with 1894 tests, 0 failures, 0 errors, and 65 skipped tests.
That run preceded the explicit property guard, protected rootkeys, Redis exclusions, and profile activation repairs.

The `chat-shell` module reports 77 tests, 0 failures, 0 errors, and 27 skipped tests.

The rebuilt image id is `sha256:279913975476175fc1a83865d705e7c514a6853b4cd595084286998cbefe05e6`.

After the final repairs, the Redis and shell integration reactor passes with 1472 tests, zero failures, zero errors, and 66 skipped tests.
Both opt-in deployment tests also pass, including authenticated startup and anonymous refusal at `rootkeys`.
The final shell image is `sha256:0f708618b9328ee217de18fb2bc3e1f926e4adbc605a31f7e48ec1fe308236a0`.
The full reactor ran again after the final repairs. `build-health.sh --ci` reports 1901 tests in 30 modules.
It reports 0 failures, 0 errors, 66 skipped tests, and no drift.
The `chat-shell` module reports 77 tests and 27 skipped tests.
That run rebuilt the shell image as `sha256:34dded7aba26cd2a5dc711e08784380bdef5f6a6e76c6679acbd73f4e9303e47`.

## Scope

This work covers REST bearer relay and core validation.

This work does not change grant rows or object authorization.

This work does not remove anonymous access from routes that allow it.

This work does not replace the authorization server Service credential.

Issuer and audience acceptance belongs to `CHAT-okpgpxkj`.
