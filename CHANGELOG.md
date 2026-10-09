# Changelog

## [Unreleased]

### 2026-10-08 — Message command bus Stage 1

| Change | Reason |
|---|---|
| A send is a captured command on the `memory` bus | Independent backend handlers, caller completion waits |
| New REST and RSocket submit and status routes | Retry safety through a request ID |
| `app.command.bus` is required | Provider selection at launch |
| The legacy RSocket `message-send` rejects another sender | The sender binds to the authenticated user |
| Submit, legacy send, and status refuse an anonymous caller | Request identity needs an authenticated owner |
| Startup rejects a provider without safe-repeat evidence, such as `redis-xstream` | Every active handler needs CI evidence |
| The legacy send routes give no retry safety | Each call creates a new request ID |
| The embedded vector write locks per document id | Overlapping writes of one document failed with `Duplicate id` |

### 2026-03-24 — chat-messaging-kafka pom.xml

| Change | Reason |
|---|---|
| Added `spring-kafka` + `reactor-kafka` | Required for `KafkaTopicPubSubService` implementation |
| Added `spring-kafka-test` (test scope) | Provides `EmbeddedKafkaBroker` for integration tests |
| Removed explicit version from `spring-boot-starter` | Parent BOM already manages it |
| Removed hardcoded `2.9.5` from `jackson-dataformat-cbor` | BOM-managed; version was two major releases stale |
| Fixed `languageVersion 1.4` → `1.8` | Stale value; matches the rest of the messaging modules |
| Added explicit `test` scope to `chat-core` test-jar dependency | Scope was missing |

### 2026-03-24 — Dependency upgrade (CHAT-gxpwprlu)

| Item | From | To | Notes |
|---|---|---|---|
| `spring-boot-starter-parent` | 3.1.5 | 3.3.13 | |
| `spring-cloud-bom.version` | 2022.0.4 | 2023.0.6 | Leyton train |
| `datastax-java-driver.version` | 4.14.1 | 4.17.0 | Renamed from `cassandra-driver.version` to avoid Boot BOM collision |
| `testcontainers.version` | 1.17.6 | 1.21.4 | |
| `spring-cloud-gateway` | 4.1.0-SNAPSHOT | 4.1.9 via BOM | Hardcoded SNAPSHOT removed from `chat-gateway/pom.xml` |
| SNAPSHOT repositories | present | removed | `spring-snapshots`, `sonatype-oss-snapshot` removed from root `pom.xml` |
| Protected (held) | — | — | `kotlin.version`, `spring-cloud.version`, `spring-cloud-stream.version`, `spring-shell.version`, `spring-security.version` |

Toolchain added under `shell-scripts/`: `deps-scope.sh`, `deps-preflight.sh`, `deps-report.sh`, `deps-apply.sh`.
Audit trail under `deps-update/`. Post-mortem: `docs/20260324-CHAT-gxpwprlu.postmortem.md`.
