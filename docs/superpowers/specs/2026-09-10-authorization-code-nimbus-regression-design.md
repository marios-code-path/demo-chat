# Authorization-Code Nimbus Regression

Status: design approved in conversation on 2026-09-10. Revised on 2026-09-10
after review.

Issue: `CHAT-ijozotvi`.

Deferred contract issue: `CHAT-cvdcfczj`.

## Purpose

The root POM forces `com.nimbusds:nimbus-jose-jwt:10.0.2` above every
dependency requester.

The default build proves compilation and Spring context creation. It does not
prove that Spring Security can call Nimbus during token processing.

Add one authorization-code test that exercises token creation and decoding.

This test is a dependency compatibility guard. It is not the complete
authorization-server contract.

## Existing Baseline

`AuthorizationServerDeployTests` starts a Spring Boot context with the `memory`
profile.

The test uses `webEnvironment = RANDOM_PORT`.

The test creates an EC P-256 JWK for ES256. It supplies the JWK through
`app.oauth2.jwk.path`.

The only test method is an empty `contextLoads` method.

No repository test calls `/oauth2/authorize` or `/oauth2/token`.

The focused baseline runs one test under Java 25. The test passes without
creating a JWT.

## Selected Approach

### Partition

Add a new test class. Do not change the web environment of the deploy test.

The protocol test uses `MOCK` because it exercises the servlet chain without a
real server. The deploy test uses `RANDOM_PORT` to keep its server smoke proof.
The two tests have different ownership and context costs.

- `AuthorizationServerDeployTests` keeps `RANDOM_PORT`. It stays the deploy
  smoke test.
- `AuthorizationCodeFlowTests` uses `MOCK` with `@AutoConfigureMockMvc`. It
  owns the protocol regression.

The two contexts differ. The test context cache holds both. The run pays one
extra context start.

Move the signing-key generation into one shared test helper. Both classes use
the helper.

### Rejected partitions

A new Maven submodule adds a POM, reactor order, enforcer convergence rules,
and drift entries. The needed partition is the context configuration, not the
dependency set. The module buys nothing here.

A real runtime instance with a real HTTP client forces a real `/login`. The
`user(...)` post-processor works only inside MockMvc. Real login belongs to
`CHAT-cvdcfczj`. This option contradicts the deferred scope.

### Test approach

Use `MockMvc` with the Spring Boot test context.

Spring Security test support supplies an authenticated user named
`token-test-user`.

This approach bypasses `/login` and `CoreUserDetailsService`.

It does not bypass the authorization endpoint, consent, the token endpoint,
JWT signing, or JWT decoding.

Keep the `memory` profile. Use the existing in-memory authorization service and
registered-client repository.

Use the generated P-256 JWK. Do not add a committed signing key.

## Test Inputs

The test must own its client registration. Do not read the client from
`application.yml`. Do not hardcode the values of that file.

Two client definitions exist. `src/test/resources/application.yml` defines one
client. `src/main/resources/application.yml` defines a different client. A
test that depends on classpath order breaks in a confusing way.

Set these properties with `@DynamicPropertySource`, next to the existing
`app.oauth2.jwk.path` entry:

- `app.oauth2.client.id`
- `app.oauth2.client.client-id`
- `app.oauth2.client.secret` with the `{noop}` prefix
- `app.oauth2.client.redirect-uris[0]`
- `app.oauth2.client.additional-scopes[0]` with value `openid`
- `app.oauth2.client.additional-scopes[1]` with value `profile`
- `app.oauth2.client.client-authentication-methods[0]` with value
  `client_secret_basic`
- `app.oauth2.client.authorization-grant-types[0]` with value
  `authorization_code`
- `app.oauth2.client.requires-authorization-concent` with value `true`

`PasswordEncoderConfiguration` supplies a delegating encoder. The `{noop}`
prefix therefore works for client authentication.

The property name `requires-authorization-concent` contains a spelling error.
Do not rename it in this issue. A rename touches `Oauth2ClientProperties`,
`ClientLoader`, both `application.yml` files, and `testclient.json`.

## Authorization Flow

1. Start an authorization request at `/oauth2/authorize`.
2. Use the client identifier and redirect URI from the test properties.
3. Send one query parameter with the space-delimited value `openid profile`.
4. Supply a fixed state value.
5. Authenticate the request as `token-test-user` with Spring Security test support.
6. Assert that the server requires consent.
7. Preserve the MockMvc session from the authorization response.
8. Read the generated consent state from the hidden form field.
9. Submit consent with the same client and generated state.
10. Send one consent parameter for each scope.
11. Use the preserved session for the consent request.
12. Assert that the response redirects to the registered redirect URI.
13. Parse the authorization code from the redirect URI.
14. Send the code to `/oauth2/token`.
15. Authenticate the client with `client_secret_basic`.
16. Send `grant_type=authorization_code` and the same redirect URI.
17. Assert that the token response succeeds.
18. Read the access token and the ID token from the JSON response.
19. Decode both tokens with the configured `JwtDecoder` bean.

Keep the HTTP sequence inside one test. The sequence is one contract and must
fail as one unit.

### Consent notes

Do not send `redirect_uri` with the consent request. A consent submission
carries `client_id`, generated consent `state`, and `scope` only.

The client state and consent state are different values. The server returns
the original client state after successful consent.

The default consent page omits `openid` from the offered scopes. Post both
scopes anyway. Both scopes belong to the requested set, so validation accepts
both. The server may also approve `openid` without consent. This
implementation detail is not verified, and the test must not depend on it.

A CSRF token in the consent request is optional.
`OAuth2AuthorizationServerConfiguration.applyDefaultSecurity` ignores CSRF for
every authorization-server endpoint. Do not state CSRF as a requirement.

## Assertions

Assert these results:

- The authorization endpoint requires consent.
- The consent response contains a redirect with one authorization code.
- The token endpoint returns HTTP 200.
- The response uses the `Bearer` token type.
- The response contains a nonblank access token.
- The response contains a nonblank ID token.
- `JwtDecoder` accepts the access token signature.
- `JwtDecoder` accepts the ID token signature.
- The JOSE algorithm of the access token is ES256.
- The JWT subject is `token-test-user`.
- The access token scope contains `openid` and `profile`.

Do not assert timestamps or generated token values.

Do not assert consent-page text. Parse only the generated consent state that
the next request requires.

## Required Application Beans

`TestConfig` declares two support beans. Keep its no-argument constructor.

Add `RequiredAppBeans` as a second `@TestConfiguration` class. Add it to the
`classes` list for both authorization-server tests.

Give `RequiredAppBeans` these constructor parameters:

- `TypeUtil<Long>`
- `CoreUserDetailsService<Long>`

A missing bean then fails at context creation. `RequiredAppBeans` declares no
bean, so no application bean depends on it.

Do not put these parameters on `TestConfig`. `CoreUserDetailsService` depends
indirectly on the `ClientDiscovery` bean that `TestConfig` creates. Constructor
injection on `TestConfig` would create a circular reference.

Keep both beans that `TestConfig` declares:

- `LocalhostDiscovery` supplies the realized `ClientDiscovery` that the deploy
  profile needs.
- `IndexSearchRequestConverters` supplies the converters that index services
  need.

Remove the dead `discovery()` function in `TestConfig`. It declares no bean and
it throws `TODO()`.

A named bean contract is the better long-term form. One small test can assert
the required bean types, or `ApplicationContextRunner` can list them. That
work is out of scope for this issue.

## Failure Meaning

A linkage error during authorization, signing, exchange, or decoding is a
dependency compatibility failure.

Do not weaken the test when Nimbus or Spring Security throws that error.

If the test exposes an incompatibility, record the exact failing call. Then
change the dependency choice or the narrow integration point.

Do not change production code unless the test proves that a production path is
incompatible.

## Test Placement

Add these files:

- `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationCodeFlowTests.kt`
- one shared test helper for the generated signing key

Modify this file:

- `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerDeployTests.kt`

`spring-security-test` is already a test dependency of the module. Add no
dependency.

Do not add a container, external server, browser driver, or network dependency.

The tests belong in the default Maven test lane. They must run during normal
pull request CI.

Run the focused check with Java 25:

```bash
mvn -o -B -pl chat-authorization-server -am \
  -Dtest='AuthorizationCodeFlowTests,AuthorizationServerDeployTests' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The active Maven JVM must support class-file version 69.

## Out of Scope

This issue does not test these behaviors:

- The real `/login` form.
- Password verification.
- `CoreUserDetailsService` authentication.
- Stored-user creation or retrieval.
- Browser rendering.
- Browser cookie behavior.
- Refresh-token behavior.
- Token revocation or introspection.
- Failure responses for invalid clients, codes, redirects, or scopes.
- JDBC authorization storage.
- Container or deployed-image behavior.

## Deferred Authorization-Server Contract

`CHAT-cvdcfczj` owns the complete authorization-server contract.

Start that work after the authentication surface stabilizes.

That issue will define real login, stored users, consent, sessions, refresh,
metadata, JWKS behavior, and selected failure paths.

The deferred issue does not block this regression test.

## Acceptance Criteria

- A new test class exercises `/oauth2/authorize` and `/oauth2/token`.
- The deploy test keeps `RANDOM_PORT` and stays green.
- The new test uses a mocked authenticated user.
- The new test sets its own client properties with `@DynamicPropertySource`.
- The test completes an authorization-code exchange with both scopes.
- The configured `JwtDecoder` accepts the access token and the ID token.
- The test checks ES256, the user subject, and both approved scopes.
- The test fails on a Nimbus runtime linkage error.
- `RequiredAppBeans` states the required application beans in its constructor.
- The focused Java 25 Maven command passes.
- `drift check` passes.
- No production behavior changes unless the test proves an incompatibility.
