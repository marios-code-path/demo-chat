# Dependency Update Plan
**Issue:** CHAT-gxpwprlu
**Date:** 2026-03-23
**Status:** completed in working tree, not yet committed

## Summary

Upgrade dependency versions across the active root Maven reactor using the Maven Versions Plugin — deterministic, no manual token-burning analysis.

## Outcome

This plan completed in the current working tree with a clean `mvn -T4 compile -DskipTests` across all 27 active modules.

Post-mortem: [`docs/20260324-CHAT-gxpwprlu.postmortem.md`](./20260324-CHAT-gxpwprlu.postmortem.md)

### Resulting State

| Item | Final Value | Notes |
|---|---|---|
| `spring-boot-starter-parent` | `3.3.13` | parent version updated |
| `spring-boot.version` | `3.3.13` | pinned boundary |
| `spring-cloud-bom.version` | `2023.0.6` | pinned boundary |
| `datastax-java-driver.version` | `4.17.0` | renamed from `cassandra-driver.version` to avoid Boot BOM collision |
| `testcontainers.version` | `1.21.4` | updated where centrally managed |
| `spring-cloud-gateway` | `4.1.9` via BOM | hardcoded `4.1.0-SNAPSHOT` removed from `chat-gateway/pom.xml` |
| SNAPSHOT repos | removed | `spring-snapshots` and `sonatype-oss-snapshot` removed from root `pom.xml` |

### Scripts Added

- `shell-scripts/deps-scope.sh`
- `shell-scripts/deps-preflight.sh`
- `shell-scripts/deps-report.sh`
- `shell-scripts/deps-apply.sh`

### Key Deviations From Original Plan

- Step 3 could not be a blind `versions:update-*` pass. It required exclusions for high-risk properties including Kotlin, Spring Cloud, Spring Cloud Stream, Spring Shell, Spring Security, gateway, and the repo-local DataStax driver property.
- `chat-gateway` did not inherit the gateway version from the root property. Its hardcoded `4.1.0-SNAPSHOT` versions had to be removed manually so the Spring Cloud BOM could supply GA versions.
- `cassandra-driver.version` in the root POM conflicted with Spring Boot 3.3.13 BOM management because Boot uses `org.apache.cassandra` coordinates while this repo depends on `com.datastax.oss`. The fix was to introduce `datastax-java-driver.version` as a repo-local property pinned to `4.17.0`.
- The first smoke-test failures were repository/model-resolution issues, not source breakages. After fixing the Cassandra property collision and clearing cached `.lastUpdated` files, compile passed cleanly.

## Scope

This plan applies to modules aggregated by the root [`pom.xml`](../../pom.xml) reactor.

The following modules are explicitly **out of scope** for this issue and should not be treated as prune candidates:

- `chat-streams`
- `chat-messaging-pulsar`
- `chat-deploy-stream-rabbit`

They support separate objectives and should be tracked independently from `CHAT-gxpwprlu`.

### Starting Baseline (`pom.xml`)

| Property | Current Version | Note |
|---|---|---|
| `spring-boot-starter-parent` | 3.1.5 | |
| `kotlin.version` | 1.8.0 | |
| `spring-cloud-bom.version` | 2022.0.4 | |
| `spring-cloud-gateway` | 4.1.0-SNAPSHOT | **must become GA** |
| `jackson.version` | 2.14.1 | |
| `io-reactor.version` | 3.6.4 | |
| `cassandra-driver.version` | 4.14.1 | |

### Primary Tool

```
org.codehaus.mojo:versions-maven-plugin
```

- **Rollback:** `mvn versions:revert` (writes `.versionsBackup` alongside each `pom.xml`)
- **Commit:** `mvn versions:commit`

---

## Execution Order

### Step 1 — CHAT-ibeixgvb: Pin Spring Boot + Spring Cloud BOM compatible versions

**Blocks:** everything else (no dependencies)

**What:** Manually determine and set the compatible (Spring Boot, Spring Cloud BOM) version pair before running automated updates.

**Why:** These two must be co-compatible; the versions plugin cannot resolve this cross-project constraint.

**How:**
- Consult https://spring.io/projects/spring-cloud for the release table
  - Spring Boot 3.3.x → Spring Cloud 2023.0.x (Leyton)
  - Spring Boot 3.4.x → Spring Cloud 2024.0.x (Moorgate)
- Choose a pair, manually update root `pom.xml` `<properties>` block:
  - `spring-boot.version`, `spring-cloud-bom.version`
- Do **not** run `versions:update-parent` yet (that happens in step 3)

**Files:** `pom.xml` (properties block only)

**Actual Result:** Root `pom.xml` was pinned to `spring-boot.version=3.3.13` and `spring-cloud-bom.version=2023.0.6` in the working tree.

---

### Step 2 — CHAT-bflcugvb: Run versions:display-* and capture update report

**Depends on:** CHAT-ibeixgvb

**What:** Run all display commands (read-only) and save output for review.

**Why:** Full inventory of available updates before touching any file.

**How:**
```bash
mkdir -p deps-update
mvn versions:display-parent-updates     > deps-update/report-parent.txt
mvn versions:display-property-updates   > deps-update/report-properties.txt
mvn versions:display-dependency-updates > deps-update/report-deps.txt
mvn versions:display-plugin-updates     > deps-update/report-plugins.txt
```

**Files:** `deps-update/*.txt` (new, committed for audit trail)

**Actual Result:** All four report files were written under `deps-update/` and used to define a conservative Step 3 exclusion set.

---

### Step 3 — CHAT-byxeguub: Apply GA-only updates via versions plugin

**Depends on:** CHAT-bflcugvb

**What:** Apply automated updates conservatively, GA releases only, with explicit exclusions for high-risk properties and modules that did not inherit versions cleanly.

**How:**
```bash
mvn versions:update-parent
mvn versions:update-properties \
  -DgenerateBackupPoms=true \
  -DexcludeProperties=kotlin.version,spring-boot.version,spring-cloud-bom.version,spring-cloud.version,spring-cloud-stream.version,spring-cloud-gateway.version,datastax-java-driver.version,spring-shell.version,spring-security.version
mvn versions:use-latest-releases \
  -DgenerateBackupPoms=true \
  -Dincludes=org.testcontainers:*

# Review before committing
git diff
```

**Notes:**
- `versions:update-parent` had to run before any Boot-coupled property reconciliation
- `chat-gateway` did not inherit its gateway version from a root property; its hardcoded `4.1.0-SNAPSHOT` dependencies were removed manually in step 4 so the BOM could supply GA versions
- `cassandra-driver.version` could not remain as a root property name because it collided with Spring Boot 3.3.13 BOM management; the repo-local property became `datastax-java-driver.version=4.17.0`
- Testcontainers was the only dependency family intentionally allowed through `use-latest-releases`

**Files:** all `pom.xml` files (modified in-place with backups)

**Actual Result:** Safe GA updates landed in the working tree: parent `3.3.13`, boundary properties held, Testcontainers moved to `1.21.4`, and the Cassandra driver property was renamed and pinned to a real DataStax release.

---

### Step 4 — CHAT-amuvknzn: Remove SNAPSHOT repository declarations

**Depends on:** CHAT-byxeguub

**What:** After GA updates are applied, SNAPSHOT repo entries in root `pom.xml` become dead weight and a liability.

**How:**
```bash
rg -n SNAPSHOT pom.xml chat-gateway/pom.xml
```

Then:
- remove hardcoded `4.1.0-SNAPSHOT` Spring Cloud Gateway versions from `chat-gateway/pom.xml`
- remove the orphaned `spring-cloud-gateway.version` property from root `pom.xml`
- remove `spring-snapshots` and `sonatype-oss-snapshot` from root `<repositories>` and `<pluginRepositories>`

**Files:** root `pom.xml`, `chat-gateway/pom.xml`

**Actual Result:** SNAPSHOT repository declarations were removed from root `pom.xml`, milestone repositories were retained, and gateway now resolves a GA version from the Spring Cloud BOM.

---

### Step 5 — CHAT-ahhfcuon: Smoke test — compile all modules, flag code-change issues

**Depends on:** CHAT-ibeixgvb, CHAT-byxeguub

**What:** Compile-only build to surface any API-break errors introduced by version bumps.

**Why:** Kotlin 1.x → 2.x has known API removals; Spring 6.x had deprecations. Compile errors are the signal.

**How:**
```bash
mvn -T4 compile -DskipTests 2>&1 | tee deps-update/compile-output.txt
```

For each compile error:
- Create a child fp issue: `fp issue create --title "Code migration: <artifact> <old>→<new>" --parent CHAT-gxpwprlu`
- That issue is a **plan marker** — implementation is separate work

**Actual Result:** The first compile attempt failed before source compilation because `cassandra-driver.version=4.18.1` was invalid for this repo's explicit `com.datastax.oss` coordinates. After renaming the property to `datastax-java-driver.version`, pinning it to `4.17.0`, updating the Cassandra modules to use that property, and clearing cached `.lastUpdated` files, `mvn -T4 compile -DskipTests` passed across all 27 active modules. No code-migration plan markers were needed.

---

## Dependency Graph

```
CHAT-ibeixgvb  (Step 1: pin Boot+Cloud)
      │
CHAT-bflcugvb  (Step 2: display report)
      │
CHAT-byxeguub  (Step 3: apply GA updates)
      │
CHAT-amuvknzn  (Step 4: remove SNAPSHOT repos)

CHAT-ibeixgvb ──┐
CHAT-byxeguub ──┴── CHAT-ahhfcuon  (Step 5: smoke test)
```

---

## Execution Plan

Drive this issue with scripts and code tools wherever the outcome is deterministic. Keep the manual surface area limited to the compatibility boundary that tools cannot infer reliably.

### Manual Boundary

The only required manual decision in this plan is choosing a compatible pair for:

- `spring-boot.version`
- `spring-cloud-bom.version`

That decision should be captured in a small input file such as:

```bash
# deps-update/deps-boundary.env
SPRING_BOOT_VERSION=...
SPRING_CLOUD_BOM_VERSION=...
```

Everything else should be automated.

### Tooling

Prefer repo-local and standard code tools:

- `mvn`
- `xmllint`
- `jq`
- `rg`
- `git`

Do not rely on manual per-`pom.xml` editing except for the root compatibility boundary.

### Proposed Scripts

The implemented dependency-update toolchain ended up smaller than originally proposed:

1. `deps-scope.sh`
   - Parse root `pom.xml` with `xmllint`
   - Emit active reactor modules to `deps-update/reactor-modules.txt`
   - Emit current root properties to `deps-update/current-properties.json`
   - Emit excluded separate-objective modules to `deps-update/excluded-modules.txt`

2. `deps-preflight.sh`
   - Validate required tools are installed
   - Check git worktree status
   - Check for existing `SNAPSHOT` usage with `rg`
   - Confirm excluded modules are not in the active reactor
   - Write machine-readable results to `deps-update/preflight.json`

3. `deps-report.sh`
   - Load `deps-update/deps-boundary.env`
   - Apply only the root Boot/Cloud compatibility values
   - Run:
     ```bash
     mvn versions:display-parent-updates
     mvn versions:display-property-updates
     mvn versions:display-dependency-updates
     mvn versions:display-plugin-updates
     ```
   - Save raw reports and a normalized summary under `deps-update/`

4. `deps-apply.sh`
   - Run `versions:update-parent` first
   - Apply a constrained `versions:update-properties` pass with an explicit exclusion list
   - Apply `versions:use-latest-releases` only for Testcontainers
   - Save:
     - `deps-update/pom-changes.diff`
     - `deps-update/post-apply-status.txt`
   - Do not commit yet

Compile verification and final review were done directly with Maven and git rather than additional wrapper scripts.

### Execution Order

Run the scripts in this order:

1. `deps-scope.sh`
2. `deps-preflight.sh`
3. Manually set `deps-update/deps-boundary.env`
4. `deps-report.sh`
5. `deps-apply.sh`
6. Run direct review and verification commands:
   - `git diff`
   - `rg -n SNAPSHOT pom.xml chat-gateway/pom.xml`
   - `mvn -T4 compile -DskipTests`

### Expected Outputs

The issue should leave behind a durable audit trail under `deps-update/`:

- reactor scope inventory
- current property inventory
- preflight results
- versions plugin reports
- `git diff` capture of `pom.xml` changes
- compile output

### Rationale

The indeterminate boundary here was compatibility policy plus a small number of repo-specific version-management mismatches. Scripts owned XML parsing, report generation, and most update application. Human intervention was still required for the Boot/Cloud pair, the gateway SNAPSHOT removal, and the Cassandra property collision that the versions plugin could not resolve safely on its own.

---

## Retired Issues (superseded by versions-plugin approach)

| Issue | Title |
|---|---|
| CHAT-zhnphsxo | Audit current dependency versions across all 30 pom.xml files |
| CHAT-yfhpmzyu | Research target versions and compatibility matrix |
| CHAT-ylrrummq | Generate per-pom.xml diff files for safe dependency updates |
| CHAT-gdtuzsso | Write applicator program to replay diffs onto target pom.xml files |
| CHAT-qvazmylk | Create plan-marker issues for dependencies requiring code changes |
