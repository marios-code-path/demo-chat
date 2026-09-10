# Authorization-Code Nimbus Regression

Status: design approved in conversation on 2026-09-10.

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

The test creates an EC P-256 JWK for ES256. It supplies the JWK through
`app.oauth2.jwk.path`.

The only test method is an empty `contextLoads` method.

No repository test calls `/oauth2/authorize` or `/oauth2/token`.

The focused baseline runs one test under Java 25. The test passes without
creating a JWT.

## Selected Approach

Use `MockMvc` with the existing Spring Boot test context.

Spring Security test support supplies an authenticated user named
`token-test-user`.

This approach bypasses `/login` and `CoreUserDetailsService`.

It does not bypass the authorization endpoint, consent, the token endpoint,
JWT signing, or JWT decoding.

Keep the `memory` profile. Use the existing in-memory authorization service and
registered-client repository.

Use the generated P-256 JWK. Do not add a committed signing key.

Replace the empty `contextLoads` method with the authorization-code test. The
new test also proves that the application context starts.

## Authorization Flow

1. Start an authorization request at `/oauth2/authorize`.
2. Use the configured client identifier and registered redirect URI.
3. Request one configured scope.
4. Supply a fixed state value.
5. Authenticate the request as `token-test-user` with Spring Security test support.
6. Assert that the server requires consent.
7. Preserve the MockMvc session from the authorization response.
8. Submit consent with the same client, state, redirect URI, and scope.
9. Use the preserved session for the consent request.
10. Include a valid CSRF token in the consent request.
11. Assert that the response redirects to the registered redirect URI.
12. Parse the authorization code from the redirect URI.
13. Send the code to `/oauth2/token`.
14. Authenticate the client with `client_secret_basic`.
15. Send `grant_type=authorization_code` and the registered redirect URI.
16. Assert that the token response succeeds.
17. Read the access token from the JSON response.
18. Decode the access token with the configured `JwtDecoder` bean.

Keep the HTTP sequence inside one test. The sequence is one contract and must
fail as one unit.

## Assertions

Assert these results:

- The authorization endpoint requires consent.
- The consent response contains a redirect with one authorization code.
- The token endpoint returns HTTP 200.
- The response uses the `Bearer` token type.
- The response contains a nonblank access token.
- `JwtDecoder` accepts the access token signature.
- The JOSE algorithm is ES256.
- The JWT subject is `token-test-user`.
- The JWT scope contains the approved scope.

Do not assert timestamps or generated token values.

Do not parse or assert consent-page HTML beyond the response behavior that the
flow requires.

## Failure Meaning

A linkage error during authorization, signing, exchange, or decoding is a
dependency compatibility failure.

Do not weaken the test when Nimbus or Spring Security throws that error.

If the test exposes an incompatibility, record the exact failing call. Then
change the dependency choice or the narrow integration point.

Do not change production code unless the test proves that a production path is
incompatible.

## Test Placement

Modify this file:

- `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerDeployTests.kt`

Do not add a container, external server, browser driver, or network dependency.

The test belongs in the default Maven test lane. It must run during normal pull
request CI.

Run the focused check with Java 25:

```bash
mvn -o -B -pl chat-authorization-server -am \
  -Dtest=AuthorizationServerDeployTests \
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

- The default test exercises `/oauth2/authorize` and `/oauth2/token`.
- The test uses a mocked authenticated user.
- The test completes an authorization-code exchange.
- The configured `JwtDecoder` accepts the returned access token.
- The test checks ES256, the user subject, and the approved scope.
- The test fails on a Nimbus runtime linkage error.
- The focused Java 25 Maven command passes.
- `drift check` passes.
- No production behavior changes unless the test proves an incompatibility.
