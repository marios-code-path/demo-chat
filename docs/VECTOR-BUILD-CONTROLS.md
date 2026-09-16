# Vector build controls

What the build decides about vector storage and rebuild, and what it leaves to
launch.

Element names are the stable reference here. The line numbers were read on
2026-09-13 and they move.

## The shape of the split

**The build decides what is possible. Launch decides what happens.**

This follows decision 1 of the capability design. One classpath carries every
provider, and selection happens at launch rather than at build.

**The vector index job record work added no build change.** No pom differs
between `master` and that work. Fifteen tasks and four repairs touched source
and tests only, and every control they added is a property.

## What the build decides

### One reactor module for each provider

`pom.xml` lists three, under `<modules>`.

| Module | Selector value |
|--------|----------------|
| `chat-vector-simple` | `simple` |
| `chat-vector-redis` | `redis` |
| `chat-vector-embedded` | `embedded` |

Each supplies a `VectorStore` bean behind `app.service.core.vector`.

### A deployment can select only what it depends on

This is the one place the build constrains behaviour.

| Deploy module | Providers on its classpath |
|---------------|----------------------------|
| `chat-deploy-memory` | `chat-vector-simple`, `chat-vector-embedded` |
| `chat-deploy-redis` | `chat-vector-redis` |

`app.service.core.vector=redis` names a legal pair, so the selector validation
accepts it. The memory deployment has no redis provider, so the bean then fails
on a missing `VectorStore`.

**This reading comes from the dependency lists, and no test measures it.** It is
the same obscure failure shape that `CHAT-aoqghxmf` records.

### The incubator flag stays narrow

The JDK Vector API is an incubator module. Code that loads it needs
`--add-modules jdk.incubator.vector`, and code that does not must not carry it.

`chat-vector-embedded/pom.xml` adds the flag twice. The compiler plugin takes it
through `<compilerArgs>`, and surefire takes it through `<argLine>`.

`chat-deploy-memory/pom.xml` repeats the surefire `<argLine>`. The embedded
store loads the Vector API inside that module's test JVM, so the flag must reach
that JVM too.

**There is no scalar fallback.** A missing flag raises
`NoClassDefFoundError: jdk/incubator/vector`, inside the library's own fallback
logging. `VectorSelectorValidation` turns that into a startup failure with a
message, so it lands at boot rather than at the first recall.

### One flag property reaches four builds

`pom.xml` declares `vector.api.module.flag`. Four places read it.

| Consumer | What it builds |
|----------|----------------|
| `pom.xml`, `BPE_APPEND_JAVA_TOOL_OPTIONS` | the deployment image |
| `chat-deploy/pom.xml`, `<buildArg>` | the GraalVM native image |
| `chat-deploy-memory-integration-test/pom.xml` | the image the shell tests use |
| `chat-authorization-server/pom.xml`, `<buildArg>` | that server's native image |

A change to the property reaches all four. Read all four before changing it.

`--enable-native-access=ALL-UNNAMED` travels beside it in the deploy runtimes.
That flag is broad on purpose, because Netty uses native access on Java 25.

### Two profiles decide what runs

`excluded.test.groups` defaults to `integration` in `pom.xml`, and surefire
reads it through `<excludedGroups>`. So a plain build runs no container test.

The `integration` profile sets that property to empty. The `test-build` profile
in `chat-deploy-memory-integration-test/pom.xml` sets `<defaultGoal>` to
`spring-boot:build-image`.

**The full gate must use `verify`, not `test`.** The test image is built in the
`package` phase, and `chat-shell` runs after that module in reactor order.

```bash
mvn -o -B clean verify -Ptest-build,integration
```

## What launch decides

| Property | Values | Effect |
|----------|--------|--------|
| `app.service.core.vector` | `mock`, `simple`, `redis`, `embedded` | which store |
| `app.service.core.embedding` | `mock` | which model |
| `app.vector.index.trust` | `none`, `stored` | whether an earlier process start counts as coverage |
| `app.vector.index.startup` | `report`, `rebuild` | whether readiness starts one rebuild |

`none` and `report` are the defaults. The selector pair is validated at startup,
and only four pairs are legal. Every legal pair uses `mock` as the embedding.

### One control is derived, not configured

`VectorWriteMode.forVectorSelector` reads the vector selector when the indexer
bean is built. It answers `DELETE_THEN_ADD` for `embedded`, and `UPSERT` for
`mock`, `simple`, and `redis`.

It is not a property, and an unknown selector is refused rather than defaulted.
**The wrong mode is silent on three of the four providers**, so a default would
give a new provider a policy with no decision behind it.

Only the embedded provider refuses a repeated document id. A removal before
every write would be wasted work on `simple`, and `RedisVectorStore` logs an
error when a delete removes no document.

### The actuator needs two properties, not one

The deployments load `management-defaults.yml`, which disables every actuator
endpoint. So exposure alone answers 404.

```properties
management.endpoint.vectorindex.enabled=true
management.endpoints.web.exposure.include=vectorindex
```

No deployment in this repository sets either value.

## The seam worth remembering

The build puts every provider on one classpath. It adds the JVM flags that the
narrowest provider needs, and it keeps the incubator flag away from everything
else. Launch picks among the providers.

The build surprises a reader in one way only. A selector can name a provider
that the deploy module never declared.

## Related

- `docs/VECTOR-RECALL-API.md` for the API and a worked scenario.
- `docs/BUILD-HEALTH.md` for the measured gate shapes and the known traps.
- `docs/NODEID-CLAIM.md` for why vector tests claim no node id.
