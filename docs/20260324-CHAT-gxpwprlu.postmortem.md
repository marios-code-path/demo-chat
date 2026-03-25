# CHAT-gxpwprlu Post-Mortem

**Issue:** CHAT-gxpwprlu
**Date:** 2026-03-24
**Status:** completed in working tree, not yet committed

## Summary

The dependency-refresh plan succeeded across the active root reactor. The working tree now compiles cleanly with `mvn -T4 compile -DskipTests` across all 27 active modules after a constrained update pass, targeted manual fixes, and removal of legacy SNAPSHOT-only wiring.

## Final State

| Item | Final Value | Notes |
|---|---|---|
| `spring-boot-starter-parent` | `3.3.13` | updated from `3.1.5` |
| `spring-boot.version` | `3.3.13` | pinned boundary |
| `spring-cloud-bom.version` | `2023.0.6` | pinned boundary |
| `datastax-java-driver.version` | `4.17.0` | repo-local DataStax property |
| `testcontainers.version` | `1.21.4` | safe update applied |
| Spring Cloud Gateway | BOM-managed GA | hardcoded `4.1.0-SNAPSHOT` removed |
| SNAPSHOT repositories | removed | `spring-snapshots` and `sonatype-oss-snapshot` removed from root |

## What Actually Happened

1. Step 1 pinned the Boot/Cloud boundary in root `pom.xml`.
2. Step 2 generated read-only Maven Versions reports and used them to identify unsafe majors and properties that needed protection.
3. Step 3 could not be a blind `versions:update-*` run. It required an exclusion list for Kotlin, Spring Cloud, Spring Cloud Stream, Spring Cloud Gateway, Spring Shell, Spring Security, and the repo-local DataStax property. Testcontainers was the only dependency family intentionally allowed through `use-latest-releases`.
4. Step 4 required manual cleanup because `chat-gateway/pom.xml` hardcoded `4.1.0-SNAPSHOT` rather than inheriting from the Spring Cloud BOM. The hardcoded versions were removed, the orphaned root gateway property was deleted, and legacy SNAPSHOT repositories were removed from root `pom.xml`.
5. Step 5 initially failed before source compilation because dependency resolution was broken for Cassandra driver artifacts. After correcting the property model and clearing stale `.lastUpdated` cache files, compile passed across the active reactor.

## Root Cause Analysis

### 1. Planned automation was too optimistic

The original plan assumed the Maven Versions Plugin could safely apply most changes across the reactor. That held for report generation and some controlled property updates, but not for every versioned seam in the repo. A few properties were compatibility boundaries, not simple “latest GA” candidates.

### 2. Spring Cloud Gateway was not actually property-managed

The plan initially treated `spring-cloud-gateway.version` as if the root property controlled the gateway version everywhere. In practice, `chat-gateway/pom.xml` hardcoded `4.1.0-SNAPSHOT` directly in its dependencies. The fix had to be a manual deletion of those hardcoded versions so the Spring Cloud BOM could provide a GA version.

### 3. Cassandra driver property name collided with Spring Boot BOM management

This was the main technical failure. The repo used a root property named `cassandra-driver.version` for explicit `com.datastax.oss` driver coordinates. After the Boot parent moved to `3.3.13`, that property name collided with Spring Boot BOM management, which uses the same property name for different Cassandra coordinates. A scripted “sync with Boot BOM” step then pulled `4.18.1`, which does not exist in Maven Central for `com.datastax.oss`.

The fix was:
- stop trying to auto-sync that property from Spring Boot
- rename the repo-local property to `datastax-java-driver.version`
- pin it to a real DataStax release, `4.17.0`
- update the Cassandra modules to reference the renamed property

### 4. Initial smoke-test failure was a repository/cache symptom, not source fallout

The first `compile` failure looked like a generic dependency-resolution problem. The real issue was an invalid version combined with cached `.lastUpdated` misses under `~/.m2/repository`. Once the property collision was fixed, clearing those cache markers allowed a clean dependency resolve and compile run.

## What Went Well

- The repo-wide update effort was kept mostly deterministic by using scripts for scope discovery, preflight checks, reporting, and the constrained apply pass.
- The boundary between “safe to automate” and “requires judgment” became clear quickly after the reports and first apply attempts.
- Compile-only verification across all active modules was enough to confirm that no immediate code-migration follow-up issues were required for this pass.

## What Did Not Go Well

- The first Step 3 design was too broad. It underestimated the number of properties that needed explicit protection.
- The initial script ordering was wrong. `versions:update-parent` needed to happen before any Boot-coupled version reconciliation.
- The planned script set was larger than necessary. Direct Maven and git commands were simpler for final review and verification than adding more wrappers.

## Follow-Up Recommendations

- Keep `deps-apply.sh` conservative. Treat it as a constrained update tool, not a blanket “upgrade everything” command.
- Preserve the renamed `datastax-java-driver.version` property. Do not reintroduce `cassandra-driver.version` at the root for the DataStax artifacts.
- Keep major-version work separate from this issue. Kotlin 2.x, Spring Shell 4.x, and Spring Security 7.x should remain dedicated follow-up efforts with explicit migration scope.
- Consider a small cleanup follow-up for build warnings that did not block compile:
  - deprecated `LATEST` / `RELEASE` usage in `chat-security`
  - duplicate `spring-boot-maven-plugin` declarations in `chat-deploy` and `chat-deploy-redis`

## Artifacts

- `deps-update/deps-boundary.env`
- `deps-update/report-parent.txt`
- `deps-update/report-properties.txt`
- `deps-update/report-deps.txt`
- `deps-update/report-plugins.txt`
- `deps-update/pom-changes.diff`
- `deps-update/compile-output.txt`
- `shell-scripts/deps-scope.sh`
- `shell-scripts/deps-preflight.sh`
- `shell-scripts/deps-report.sh`
- `shell-scripts/deps-apply.sh`
