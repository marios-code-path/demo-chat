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

| Database state | Operator action | Application action |
| --- | --- | --- |
| Empty database | None | Flyway applies versions 1 and 2. |
| Existing three-table database | Verify the three table definitions, then baseline at version 1. | Flyway applies version 2. |
| Existing four-table database | Verify all four table definitions and the replay index, then baseline at version 2. | Flyway records no new schema change. |

Flyway does not baseline a nonempty database automatically.
The server refuses startup when that database has no Flyway history.
The operator selects the baseline version after checking the existing schema.
The operator must not baseline a database with missing or different required objects.

## Runtime and deployment

The JDBC Maven profile adds the Spring Boot Flyway starter and the PostgreSQL Flyway module.
Spring Boot runs migrations before the authorization server uses JDBC tables.
The memory profile does not need a database or Flyway.

Docker Compose and Kubernetes stop mounting SQL files into the Postgres initialization directory.
The versioned migration files become the only active schema source.
The deployment guide gives an exact baseline command for each existing database state.
The guide also tells the operator how to check the schema before that command.

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
