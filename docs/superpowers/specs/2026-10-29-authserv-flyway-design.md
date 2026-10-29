# Authorization server Flyway design

## Purpose

The authorization server now uses four JDBC tables.
Docker Compose runs the current SQL files only when Postgres creates a new volume.
Flyway will own schema changes for new and existing databases.
This work follows `CHAT-wdvjqtpb` and belongs to `CHAT-uupbvnzq`.

## Database states

The first Flyway migration creates the three original OAuth tables.
The second migration creates `oauth2_client_assertion_jti` and its expiry index.
The migrations use the current SQL definitions without unrelated schema changes.
The implementation removes the four old files from `chat-authorization-server/sql-scripts`.

Count only these application tables when selecting a baseline:

- `oauth2_registered_client`
- `oauth2_authorization`
- `oauth2_authorization_consent`
- `oauth2_client_assertion_jti`

Do not include `flyway_schema_history` or unrelated tables in that count.
Check Flyway history separately. Do not run baseline if it already exists.

| Database state | Operator action | Application action |
| --- | --- | --- |
| Empty database | None | Flyway applies versions 1 and 2. |
| Existing three-table database | Verify the three application table definitions, then baseline at version 1. | Flyway applies version 2. |
| Existing four-table database | Verify all four application table definitions and the replay index, then baseline at version 2. | Flyway records no new schema change. |

Flyway does not baseline a nonempty database automatically.
Set `spring.flyway.baseline-on-migrate=false` in the JDBC runtime configuration.
The server refuses startup when that database has no Flyway history.
The operator selects the baseline version after checking the existing schema.
The operator must not baseline a database with missing or different required objects.

## Baseline tool

The operator runs the pinned `flyway/flyway:11.14.1-alpine` CLI container.
Spring Boot 4.0.8 manages the same Flyway version.
This tool connects to the same Postgres database as the authorization server.
The CLI container runs once before the new server image starts.
The application does not run the `baseline` command.

For Docker Compose, export the connection values without placing the password in a command argument:

```sh
export FLYWAY_URL='jdbc:postgresql://authserv-postgres:5432/authserver'
export FLYWAY_USER="$SPRING_DATASOURCE_USERNAME"
export FLYWAY_PASSWORD="$SPRING_DATASOURCE_PASSWORD"
```

For an existing three-table database, run:

```sh
docker run --rm --network "${CHAT_NETWORK:-docker_chatnet}" \
  -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD \
  flyway/flyway:11.14.1-alpine -baselineVersion=1 baseline
```

For an existing four-table database, run the same command with `-baselineVersion=2`:

```sh
docker run --rm --network "${CHAT_NETWORK:-docker_chatnet}" \
  -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD \
  flyway/flyway:11.14.1-alpine -baselineVersion=2 baseline
```

The deployment guide must give a separate Kubernetes Job example for the same CLI image.
That Job must use operator-supplied database credentials.
It must pass `-baselineVersion=1` or `-baselineVersion=2` explicitly.
The operator must verify the four named application tables before choosing the Job argument.

## Runtime and deployment

The JDBC Maven profile adds the Spring Boot Flyway starter and the PostgreSQL Flyway module.
Spring Boot runs migrations before the authorization server uses JDBC tables.
The memory profile does not need a database or Flyway.

Remove these active schema paths:

- `chat-authorization-server/make-sql-schema-config-map.sh`
- `chat-authorization-server/get-auth-jdbc-schema.sh`
- The `sql-files` ConfigMap volume in `chat-authorization-server/kustomize/base/deployment.yaml`
- The `sql-scripts` mount in `chat-authorization-server/docker-compose.yaml`

Docker Compose and Kubernetes must not mount SQL files into the Postgres initialization directory.
The versioned migration files become the only active schema source.
The deployment guide must include both CLI commands and the table checks.
Update `docs/MCP-CREDENTIAL-ISSUANCE.md` to remove its old `psql` schema command.

Keep the startup check in `JdbcAssertionReplayStore` as a backstop.
Update its failure message to name `src/main/resources/db/migration/V2__client_assertion_replay.sql`.
This check detects an incorrect baseline or a missing replay table.

The application fails startup if Flyway cannot apply a migration.
The operator takes a database snapshot before the first production migration.
For an application rollback, use the previous image and keep the extra Flyway tables.
If a migration damages data, restore the database snapshot taken before the migration.
Do not delete the replay table during an application rollback.

## Verification

Tests first show failure on an empty database without Flyway migrations.
They also show failure when an existing database has no Flyway baseline.
After implementation, tests apply both migrations to an empty H2 database in PostgreSQL mode.
Tests baseline a three-table database at version 1 and a four-table database at version 2.
They count only the four named application tables.
They check `flyway_schema_history` separately after baseline.
Both tests preserve existing client data and check the Flyway history.
Repeated startup must apply no migration twice.

The JDBC endpoint test boots the Spring profile after Flyway migrates the database.
It requests one token with an RS256 assertion and checks replay rejection.
The test reads the replay record from the JDBC table.
The full module suite and pull request CI must pass before merge.

## Choice

Manual Flyway baseline protects existing databases from a wrong automatic version choice.
Automatic baseline on migrate would need one fixed version for databases with two different table sets.
Keeping Postgres initialization scripts would create a second schema owner.

Spring Boot documents automatic Flyway migrations at startup:
https://docs.spring.io/spring-boot/how-to/data-initialization.html

Flyway documents how baseline marks an existing schema version:
https://documentation.red-gate.com/flyway/reference/commands/baseline
