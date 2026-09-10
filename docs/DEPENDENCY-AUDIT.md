# Dependency Audit

What the audit is, what a person does with its output today, and where automation would attach.

This is a surface map. It names the points, not the implementations.

## What runs

`.github/workflows/dependency-audit.yml` runs OWASP dependency-check over the whole
reactor. It runs daily at 05:00 UTC and on a manual trigger.

```bash
gh workflow run dependency-audit.yml
```

The job fails when a finding scores CVSS 9.0 or above. That threshold is
deliberately high while the deployment surface is small. Lower it to 7 when that
changes.

The HTML report uploads as the artifact `dependency-check-report`, on success and
on failure.

```bash
gh run download <run-id> -n dependency-check-report
```

Related policy lives in `CHAT-mgtbicsq`. Versions have one central point, and
three checks keep them there:

- `shell-scripts/check-dependency-versions.sh` fails when a module pom declares a
  third-party version.
- `requireUpperBoundDeps` fails when a resolved version is lower than another path
  in the tree requires.
- `dependencyConvergence` fails when one artifact resolves to two versions.

The two enforcer rules run in the `validate` phase, so every build checks them.

## How to bump a version

There are two levers. Both live in the root pom. A module pom never carries a
version, so a module is never the place to change one.

### Lever 1: a BOM coordinate

A BOM moves a whole family at once. Do not bump a member of a family directly.

| Change | Moves |
|--------|-------|
| `spring-boot-starter-parent` and `spring-boot.version` | Spring Framework, Spring Security, Tomcat, Jackson, Netty, JUnit, Mockito, Testcontainers, Reactor, the Cassandra driver, and more |
| `spring-cloud-bom.version` | every `spring-cloud-*` artifact |
| the `spring-ai-bom` version | every `spring-ai-*` artifact |

This is why `jackson-dataformat-cbor` has no property. It rides the Boot BOM. A
direct pin on it is the defect that `CHAT-hcgmcuxp` removed from five modules.

### Lever 2: a property

For artifacts that no BOM covers. Each has a property in the root pom and a
matching `dependencyManagement` entry. Change the property and the whole
repository follows.

Read the current list from the root pom rather than from here, so this document
cannot go stale:

```bash
grep -oE '<[a-z0-9.-]+\.version>[^<]*' pom.xml | sort -u
```

### Which lever applies

```bash
mvn -B dependency:tree -Dverbose -pl <module> | grep <artifact>
```

- The output says `(version managed from X)`. Something manages it. If a property
  for it exists in the root pom, that property is the lever. If not, a BOM is.
- The output says nothing about management. Nothing manages it, and
  `shell-scripts/check-dependency-versions.sh` should have failed.

### After any bump

```bash
mvn -B clean test
./shell-scripts/check-dependency-versions.sh
mvn -B clean verify -Pintegration
```

The enforcer is the ripple detector. It runs in the `validate` phase, before
compilation.

- `requireUpperBoundDeps` fails when the new version is **lower** than something
  else in the tree needs.
- `dependencyConvergence` fails when the artifact now resolves to two versions.

A failure here is a real change in the tree. Fix it with a parent entry, never
with a module pin.

### What can be bumped

```bash
mvn -B versions:display-property-updates      # the root pom properties
mvn -B versions:display-dependency-updates    # everything, BOM managed included
```

### One question worth asking first

**Does anything in the tree actually request the new version?**

```bash
mvn -B dependency:tree -Dverbose -pl <module> | grep <artifact>
```

A pin that matches the highest requester resolves a conflict. A pin above every
requester forces a version that no upstream library was built or tested against.
Both are legal. The second carries risk that a green build does not rule out,
because the failure lands at runtime on the path that uses the removed API.

That is a decision to take deliberately, not by default.

## The round of actions, today

Every step below is manual. That is the reason this document exists.

1. **Read the result.** A green run means no finding at or above the gate. It does
   not mean no findings.
2. **Fetch the report.** Download the artifact and open the HTML.
3. **Sort each finding.** Decide whether it is reachable in this project, whether a
   fix exists, and whether the fix is a version bump or a replacement.
4. **Find the owner of the version.** A managed artifact moves by raising the BOM. An
   unmanaged one moves in the parent `dependencyManagement`. No module carries a
   version.
5. **Judge a transitive finding.** Most findings arrive through a dependency of a
   dependency. The fix is usually a BOM bump, not a direct pin. A direct pin
   re-creates the defect that `CHAT-hcgmcuxp` removed.
6. **Record what was decided.** A finding that is accepted rather than fixed needs a
   written reason and a review date. Nothing records that today.
7. **Re-run and confirm.**

## Where automation would attach

Seven points. Each is a place where a person currently reads, decides, or copies.

| # | Point | What a person does now |
|---|-------|------------------------|
| 1 | Report retrieval | Downloads an artifact by hand and opens HTML in a browser |
| 2 | Machine-readable output | Reads a report built for human eyes. The plugin can emit JSON and SARIF as well |
| 3 | Finding to issue | Reads a finding and decides whether to open an FP issue for it |
| 4 | Deduplication | Recognises that today's finding is the same one seen yesterday |
| 5 | Suppression with an expiry | Holds an accepted finding in their head. The plugin has a suppression file, and nothing in the repo uses it |
| 6 | Fix routing | Works out whether a finding moves by BOM bump, by parent entry, or not at all |
| 7 | Trend | Cannot answer whether the count is rising or falling. Each run stands alone |

Two properties any automation needs, whatever shape it takes:

- **It must not open a duplicate issue on every run.** A daily job with no
  deduplication produces a daily issue for the same finding.
- **It must separate a new finding from a known one.** That distinction is the whole
  signal. Without it a report is noise.

## Known limits of the current setup

- No suppression file exists. An accepted finding has no home.
- No baseline exists. The first run establishes one.
- The threshold gates the job but not the report. A finding below 9 is invisible
  unless somebody opens the HTML.
- A local run needs the NVD key from `.envrc`. The CI job needs the repository
  secret. They are two separate places.

## Related

- `CHAT-xojupyhn` holds the automation of these points.
- `CHAT-mgtbicsq` holds dependency version governance and the remaining conflict
  cleanup.
- `docs/BUILD-HEALTH.md` records build-time deficiencies and follows the same
  pattern: a document that a script checks.
