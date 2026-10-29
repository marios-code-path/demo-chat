# Authorization server Flyway implementation plan

**Issue:** `CHAT-uupbvnzq`

**Goal:** Flyway owns the authorization server JDBC schema for new and existing databases.

**Design:** `docs/superpowers/specs/2026-10-29-authserv-flyway-design.md`

## 1. Prove the missing migration

Edit `chat-authorization-server/src/test/kotlin/com/demo/chat/JdbcPrivateKeyAgentTests.kt`.
Remove `spring.sql.init.mode` and `spring.sql.init.schema-locations`.
Assert that `flyway_schema_history` contains versions 1 and 2.
Run `mvn -q -pl chat-authorization-server -am -Dtest=JdbcPrivateKeyAgentTests -Dsurefire.failIfNoSpecifiedTests=false test`.
Expect startup to fail because the JDBC tables do not exist.

## 2. Add Flyway to the JDBC runtime

Edit `chat-authorization-server/pom.xml`.
Add `spring-boot-starter-flyway` and `flyway-database-postgresql` to the `jdbc` Maven profile.
Add `spring-boot-starter-flyway` at test scope for plain CI test runs.
Copy the current three OAuth table definitions into `src/main/resources/db/migration/V1__oauth2_core.sql`.
Copy the replay table and index into `src/main/resources/db/migration/V2__client_assertion_replay.sql`.
Set `spring.flyway.baseline-on-migrate=false` for JDBC runtime.
Run the focused test again. Expect the token request to return 200 and its replay to return 401.

## 3. Prove both existing database states

Add `chat-authorization-server/src/test/kotlin/com/demo/chat/FlywaySchemaMigrationTests.kt`.
Use H2 in PostgreSQL mode and real migration resources.
Test an empty database, a database with three application tables, and one with four.
Insert a consent row before each existing database baseline.
Baseline at version 1 or 2 explicitly.
Check that migration keeps the row and creates the expected tables.
Run migration twice and check that the second run applies zero migrations.
Count only the four named application tables. Check `flyway_schema_history` separately.

## 4. Remove the second schema owner

Remove the `sql-scripts` mount from `chat-authorization-server/docker-compose.yaml`.
Remove the `sql-files` volume from `chat-authorization-server/kustomize/base/deployment.yaml`.
Delete `make-sql-schema-config-map.sh`, `get-auth-jdbc-schema.sh`, and the four old SQL files.
Update the replay table failure message in `JdbcAssertionReplayStore.kt` to name the V2 migration.
Run focused authorization server tests after these changes.

## 5. Document baseline and verify

Update `docs/MCP-CREDENTIAL-ISSUANCE.md` and `shell-scripts/README-chat-build.md`.
Add a database migration guide with table checks and both explicit Flyway CLI commands.
Add a Kubernetes Job example that uses operator-supplied credentials.
Document the database snapshot and application rollback path.
Run the full authorization server module tests, `git diff --check`, and `drift check`.
Open a pull request and wait for full CI and repository owner approval before merge.

## 6. Correct the live deployment path

Add `shell-scripts/test-authserv-launch.sh`.
Check that `just deploy-authserv` passes `jdbc` and `client-init` to both `chat-build` calls.
Stub Docker with two Consul container IDs.
Check that `chat-build` selects one running container.
Run both checks before the fixes and record their failures.
Update `justfile`, `shell-scripts/chat-build`, and `shell-scripts/README-chat-build.md`.
Run the new test, `test-flags.sh`, `test-key-type-required.sh`, and `drift check`.
