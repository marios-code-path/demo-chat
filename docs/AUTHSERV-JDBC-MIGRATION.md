# Authorization server JDBC migration

Flyway owns the authorization server JDBC schema.
The JDBC application runs pending migrations when it starts.
The memory application does not use this database.

## New database

Start Postgres, then start the new authorization server image.
Flyway creates three OAuth tables at version 1.
Flyway creates the assertion replay table and expiry index at version 2.
Do not run a baseline on an empty database.

## Existing database

Take a database snapshot before the first start with the new image.
Stop the authorization server while you inspect and baseline the database.
Connect to the same `authserver` database that the server uses.

Check the Flyway history table:

```sql
SELECT to_regclass('public.flyway_schema_history');
```

If this query returns a table name, inspect its history.
Do not run baseline again.

Count only the four application tables:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
    'oauth2_registered_client',
    'oauth2_authorization',
    'oauth2_authorization_consent',
    'oauth2_client_assertion_jti'
  )
ORDER BY table_name;
```

Check that the three OAuth tables match `V1__oauth2_core.sql`.
If the replay table exists, check its definition and expiry index against `V2__client_assertion_replay.sql`.
Stop if a required table or index has a different definition.
Unrelated tables do not affect this count.

| Application tables | Action |
| --- | --- |
| Zero | Start the new server. Flyway applies versions 1 and 2. |
| Three OAuth tables | Baseline at version 1. Flyway then applies version 2. |
| Three OAuth tables and replay table | Baseline at version 2. Flyway applies no schema change. |

The baseline records the current schema version.
It does not create a missing table.
The server does not baseline a nonempty database automatically.

### Docker Compose baseline

Use the same database credentials as the authorization server.
Set the variables in the operator shell:

```sh
export FLYWAY_URL='jdbc:postgresql://authserv-postgres:5432/authserver'
export FLYWAY_USER="$SPRING_DATASOURCE_USERNAME"
export FLYWAY_PASSWORD="$SPRING_DATASOURCE_PASSWORD"
```

For the three OAuth tables, run:

```sh
docker run --rm --network "${CHAT_NETWORK:-docker_chatnet}" \
  -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD \
  flyway/flyway:11.14.1-alpine -baselineVersion=1 baseline
```

For all four application tables, run:

```sh
docker run --rm --network "${CHAT_NETWORK:-docker_chatnet}" \
  -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD \
  flyway/flyway:11.14.1-alpine -baselineVersion=2 baseline
```

Run one baseline command only.
Then start the new authorization server image.

### Kubernetes baseline

Create a Secret named `authserv-flyway-db` with keys `url`, `user`, and `password`.
Set `url` to the JDBC URL for the authorization server database.
Use the database credentials that the operator supplied to the server.
Save this Job as `authserv-flyway-baseline.yaml`:

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: authserv-flyway-baseline
spec:
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: flyway
          image: flyway/flyway:11.14.1-alpine
          args: ["-baselineVersion=1", "baseline"]
          env:
            - name: FLYWAY_URL
              valueFrom:
                secretKeyRef: {name: authserv-flyway-db, key: url}
            - name: FLYWAY_USER
              valueFrom:
                secretKeyRef: {name: authserv-flyway-db, key: user}
            - name: FLYWAY_PASSWORD
              valueFrom:
                secretKeyRef: {name: authserv-flyway-db, key: password}
```

For four application tables, change the argument to `-baselineVersion=2`.
Apply the Job once before the new server image starts.
Check that the Job succeeds before you start the server.

## Verify and recover

After startup, check the Flyway history:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

The last version must be 2, and each row must have `success = true`.
The replay table must exist after migration.
The server also checks that table when a JWKS agent is configured.

To restore the previous application image, keep the Flyway history and replay table.
If a migration damages data, restore the database snapshot.
