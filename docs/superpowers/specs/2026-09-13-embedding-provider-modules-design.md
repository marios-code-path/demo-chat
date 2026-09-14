# Embedding Provider Modules

Status: approved. The owner approved this document at `1b791951`, after four
reviews. Two prerequisites must land first. See Security Prerequisite and Build
Prerequisite.

Plan: `docs/superpowers/plans/2026-09-14-embedding-provider-modules.md`.

Issue: `CHAT-etfnihnu`.

Related: `docs/superpowers/specs/2026-09-11-vector-index-run-record-design.md`.

## The Gap

Recall runs today only because a test jar sits on a production classpath.

`DummyEmbeddingModel` is the one `EmbeddingModel` in this repository. It lives
under `chat-core/src/test`. `MockEmbeddingConfiguration` declares the bean, also
under `src/test`.

`chat-deploy-memory` declares the `chat-core` test jar with no scope element, so
it resolves at compile. A runtime classpath read on 2026-09-13 lists
`chat-core-0.0.1-tests.jar`. That jar carries 138 classes, and 43 of them are
test classes.

`chat-deploy-redis` declares the same test jar at test scope. So a launched
redis deployment has no `EmbeddingModel`, and its vector store bean cannot
build.

**No specification, task, or test captured the requirement.** A deployment that
sets the vector selectors must supply an `EmbeddingModel`, and nothing said so.

**The test suite cannot detect this.** A Spring Boot test puts test jars on its
classpath by construction. `MemoryVectorRecallBootTests` passes under either
scope, so it proves nothing about a launch.

## Design Summary

Add two production embedding modules. Each supplies an `EmbeddingModel` behind
one selector value.

- `chat-embedding-openai` supplies `embedding=openai`.
- `chat-embedding-local` supplies `embedding=local`.

The mock stays where it is. It remains test only, in the `chat-core` test jar,
and it keeps the `mock` selector value.

`chat-core` gains a typed identity and one resolver bean. Five call sites inject
the resolved value.

A guard refuses a test artifact on a production classpath.

The final gate launches a packaged deployment with no test output on its
classpath. That deployment embeds text, rebuilds, and searches.

## The Two Providers

### Dependencies, and why not the starters

Each module depends on the model library alone.

- `chat-embedding-openai` depends on `spring-ai-openai`.
- `chat-embedding-local` depends on `spring-ai-transformers`.

**Neither module depends on a starter.** Both modules sit on one classpath, and
a starter carries auto-configuration. Two starters would let Spring AI build
models that no selector asked for. The selector must be the only thing that
decides.

So each module constructs its own model inside a configuration class, behind
`@ConditionalOnProperty`. The construction is explicit and it is conditional.

### chat-embedding-openai

The module owns three values. The operator sets all three.

- `app.service.core.embedding.openai.base-url`
- `app.service.core.embedding.openai.api-key`
- `app.service.core.embedding.openai.model`

Spring AI reaches an OpenAI-compatible endpoint through a base URL. So this
provider serves OpenAI and any service that speaks the same API.

**`OpenAiApi` accepts a `NoopApiKey` in Spring AI 1.0.3.** So the library does
not force a key.

**This design requires one anyway.** A blank key reaches a remote service as an
anonymous call, and an operator cannot tell a missing key from an intended one.
So the property is required, and that is project policy rather than a library
rule. The gate supplies a dummy value that is not a secret.

### chat-embedding-local

The module owns two values. The operator sets both.

- `app.service.core.embedding.local.model-uri`
- `app.service.core.embedding.local.tokenizer-uri`

Spring AI reads `file:`, `classpath:`, and `https:` resources. So an operator
supplies a model from disk, from the artifact, or from a remote host.

**The remote cache ignores the identity.** `ResourceCacheService` in Spring AI
1.0.3 caches a remote resource by its location. A new identity with unchanged
URIs would load the old bytes, and the corpus would carry vectors from the
previous model under the new name.

So this module sets a cache directory that carries the identity. The operator
may set the base, and the default is ephemeral.

```properties
app.service.core.embedding.local.cache-path=${java.io.tmpdir}/chat-embedding-local
```

The module passes `<cache-path>/<identity>` to the cache service. The default
follows the embedded store, whose storage is ephemeral because the corpus is a
derived cache.

An operator who wants no caching names the local resources with `file:`, which
the cache does not copy.

**This module builds against Spring AI 1.0.3.** The root pom pins that version
and a comment beside the pin advises against 2.0.0. **No enforcer rule bans
2.0.0.** The ONNX documentation for 2.0 describes a different surface, so the
implementation follows the 1.0 surface of `spring-ai-transformers`.

### Neither provider changes a vector store

A vector store receives an `EmbeddingModel` and asks nothing about its origin.
No store needs a second loading mode.

## Model Identity

**Every production model carries an identity that the operator sets.**

```properties
app.service.core.embedding.identity=acme-e5-small-v2
```

The value is required when `embedding` is `openai` or `local`. It is refused
when `embedding` is `mock`, which resolves to the fixed identity `mock`.

The value must match `[a-z0-9][a-z0-9-]{0,63}`. A redis key prefix and a
directory name both carry it, so the character set is narrow. Startup fails on
any other value.

**The identity is not derived.** A base URL and a model name do not identify the
output of a remote service. A compatible service can change its model behind
both values and return different vectors for the same text. Only an operator
knows that a change happened, so only an operator can name the identity.

**The identity is immutable for a corpus.** A new identity means a new corpus.
It does not mean a migration.

### The typed contract

`chat-core` declares the type and one resolver.

```kotlin
@JvmInline
value class EmbeddingIdentity(val value: String)
```

One bean resolves it. The bean reads the embedding selector and the identity
property, applies the rules above, and fails startup on a breach.

**The bean is absent when both vector selectors are absent.** Most deployments
set neither selector today, and they must keep starting. So the resolver carries
the same condition the recall beans carry. A deployment with no vector store
resolves no identity, and nothing asks it for one.

**Five call sites inject the resolved value.** None of them reads the property.

| Call site | Use |
|-----------|-----|
| `RedisVectorStoreConfiguration` | the index name and the key prefix |
| `EmbeddedVectorStoreConfiguration` | the collection directory |
| `LocalEmbeddingConfiguration` | the resource cache directory |
| `VectorIndexJobStoreImpl.createJob` | the value it writes on a new `IndexJob` |
| `VectorCoveragePolicyImpl` | the value it matches when it selects coverage |

A typed value stops a raw string from reaching the wrong parameter. Three of
those five call sites already take a `keyType` string, and a second string
beside it would be easy to swap.

## Where Identity Is Used

### Redis index names and prefixes

The index name and the key prefix gain the identity segment.

- Index: `chat:vector:<keyType>:<identity>:message`
- Prefix: `chat:vector:<keyType>:<identity>:message:`

A metadata field does not isolate a redis index, which is why the key type is
already in the name. The identity joins it for the same reason.

**Existing redis indexes orphan.** The old names hold vectors from an unnamed
model, and no read reaches them again.

### Embedded storage namespace

The collection directory gains the identity segment, under the configured path.

```
<app.service.core.vector.embedded.path>/<identity>
```

The embedded collection takes its width from `embeddingModel.dimensions()`, and
two models rarely share a width. A separate directory per identity means a model
change cannot meet a collection of the wrong width.

**Existing embedded directories orphan.** The rule is the same as redis.

### The stored IndexJob

`IndexJob` gains one field.

```kotlin
val embeddingIdentity: String? = null
```

The field is nullable. A record written before this change carries null, which
states that the writer named no model. An empty string would state that the
writer named an empty model, and those are different facts.

### Coverage selection

`VectorCoveragePolicyImpl` adds one filter. A job covers only when its
`embeddingIdentity` equals the identity this process resolved.

**The filter runs before the sort and before `next()`.** That order is the rule,
and it repeats the rule the `SUCCEEDED` filter already follows.

A newer job of a foreign identity would otherwise reach `next()` first. The
policy would select it, reject it, and report no coverage. A valid older job of
this identity would then be hidden behind it, and the index would rebuild for no
reason.

**This change invalidates every covering job that exists today.** Each one
carries null, and null equals no identity. So the first start after this change
reports an incomplete index and waits for a rebuild. That outcome is correct,
because those vectors came from a model that no longer has a name.

### Where identity is not used

**The release sweep does not filter on identity.** It marks the stale running
jobs of earlier incarnations as released. A stale job is stale whatever model
wrote it, and releasing it is correct in every case.

**The actuator read does not filter on identity.** An operator needs to see the
jobs of every model this node wrote. The job list carries the identity field, so
the reader can tell them apart.

## Selector Pairs

`VectorSelectorValidation` holds the legal pairs. The set grows from four to
ten.

| vector | embedding | Note |
|--------|-----------|------|
| `mock`, `simple`, `redis`, `embedded` | `mock` | the four that exist today |
| `simple`, `redis`, `embedded` | `openai` | new |
| `simple`, `redis`, `embedded` | `local` | new |

**`mock` with `openai` and `mock` with `local` are not legal.** No mock vector
configuration exists to receive a production model, so the pair would name a
store that cannot build.

The validation also gains the identity rule. A production embedding without an
identity fails startup. A `mock` embedding with an identity fails startup.

## Deployment Declarations

`chat-deploy-memory` and `chat-deploy-redis` each declare both production
modules at compile scope.

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>chat-embedding-openai</artifactId>
    <version>0.0.1</version>
</dependency>
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>chat-embedding-local</artifactId>
    <version>0.0.1</version>
</dependency>
```

**`chat-deploy-memory` also moves its test jar dependency to test scope.** The
mock then reaches tests and never reaches a launch.

A deployment can select only an embedding it declares, exactly as it can select
only a vector store it declares.

## The Production Dependency Guard

One script holds two rules. Both run over every module.

**Rule one reads the poms.** Every dependency with `<type>test-jar</type>` must
declare `<scope>test</scope>`.

**Rule two reads the resolved classpath.** A known test library must not appear
on a compile or runtime classpath. The rule reads `dependency:build-classpath`
with runtime scope, because a transitive leak never appears in a pom.

The known list starts with these groups and artifacts.

- `org.testcontainers`
- `com.redis:testcontainers-redis`
- `org.junit.jupiter`, `org.junit.platform`
- `org.mockito`, `org.mockito.kotlin`
- `org.assertj`
- `io.projectreactor:reactor-test`
- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.security:spring-security-test`

**Rule two is what closes `CHAT-incuynpc`.** That issue records two
testcontainers artifacts resolving at compile scope in `chat-shell`. They are
ordinary jars and not test jars, so rule one does not see them.

### The guard fails on ten modules today

A read on 2026-09-13 found a test jar dependency with no scope element in every
module below.

`chat-client-consul`, `chat-client-rsocket`, `chat-deploy-memory`,
`chat-messaging-memory`, `chat-persistence-memory`, `chat-security`,
`chat-service-composite`, `chat-service-controller`, `chat-shell`,
`chat-webflux`.

**The ten split into nine and one.**

`CHAT-xvtsffqh` owns **nine** of them, and it carries the two `chat-shell`
testcontainers dependencies. Each move can break a compile that relied on the
wider scope, so each needs its own build.

**This work owns `chat-deploy-memory`**, because this work removes the reason
that module was wide. The prerequisite must not also own it, or neither issue
can finish. `CHAT-etfnihnu` depends on `CHAT-xvtsffqh`, so a shared module would
deadlock the pair.

The guard installs in this work, after the memory move. By then the other nine
have already moved.

## The Packaged Launch Gate

**A test classpath cannot prove this feature works.** Every gate in this
repository puts test outputs on the classpath, which is what hid the defect. So
the gate runs outside a test classpath.

### What the deployment needs first

**`chat-deploy-memory` carries no `chat-webflux`.** It has the webflux
framework, so an actuator answers over HTTP, and it has none of this
repository's REST routes. The root pom carries an `expose-webflux` profile that
adds `chat-webflux`, and the gate builds with it.

The alternative is an RSocket client, because `app.server.proto` is `rsocket`.
The profile is smaller, and it already exists for this purpose.

The launch also sets both actuator properties, because the deployments disable
every endpoint by default.

```properties
management.endpoint.vectorindex.enabled=true
management.endpoints.web.exposure.include=vectorindex
```

### The sequence

**A send and a recall create no `IndexJob`.** A live add writes one vector and
records nothing durable. So the gate triggers a rebuild, which is the only path
that writes a job.

**The gate does not use `/message/send/{id}`.** That route binds
`@AuthenticationPrincipal ChatUserDetails`, and the actuator credentials resolve
to a plain Spring Security `User`. The principal would not bind.

So the gate seeds through persistence, which takes the sender in its body and
asks for no principal. A message that persistence holds and the index lacks is
also the exact state a rebuild exists to repair.

The launch enables two controllers.

```properties
app.controller.persistence=true
app.controller.recall=true
```

**The actuator calls carry credentials. The application calls do not.**
`CHAT-jdsamcia` is a prerequisite of this work, and it gives the two filter
chains disjoint ownership. This section states the contract that issue delivers,
because the gate rests on it.

After `CHAT-jdsamcia`, the actuator chain matches actuator routes only, through
a management aware matcher, and it takes the higher priority. The health
endpoint stays open. Every other actuator route requires the ACTUATOR role.
`WebFluxSecurity` owns all remaining routes, and it permits them today.

So the trigger and the poll carry Basic credentials for the actuator user.
`app.actuator.username` and `app.actuator.password` name that user, which lives
in memory in that chain alone. The seed and the recall reach application routes,
and they need no credentials.

**No application route authenticates today, and this work does not change that.**
`WebFluxSecurity` wires no `httpBasic` and no authentication manager, so no
`Authentication` reaches a route it owns. The gate seeds through
`PUT /persist/message/add`, which binds no principal, and it recalls through
`ChatMessageRecallController`, which binds only a request body. So the gate never
needs an application identity. `POST /message/send/{id}` does need one, at
`ChatMessageServiceRestMapping.kt:33`, and the gate does not use that route.

**Why this is a prerequisite and not a note.** Today
`ActuatorWebSecurityConfiguration` answers `anyExchange()` with the ACTUATOR
role, so it owns every route. `chat-webflux` declares `WebFluxSecurity`, which
answers `anyExchange()` with `permitAll()`. Neither bean declares an order.
`expose-webflux` puts both on one classpath, so the winner depends on bean
ordering. The gate cannot rest on an undefined outcome, and a gate that asserted
the current behaviour would make bean ordering a security contract.

1. Build `chat-deploy-memory` with `-Pexpose-webflux`, and repackage it.
2. Assert that no test output is on the launched classpath.
3. Launch with `vector=simple`, `embedding=openai`, an identity, both actuator
   properties, both controller properties, and a base URL that names the stub.
4. Seed messages with `PUT /persist/message/add`. That route belongs to
   `WebFluxSecurity`, which permits it. The write reaches persistence and creates
   no vector.
5. Trigger a rebuild through `POST /actuator/vectorindex`, under Basic
   credentials for the actuator user. The rebuild embeds every seeded message
   through the production client.
6. Poll `GET /actuator/vectorindex` under the same credentials, until `running`
   is false.
7. Assert that the newest job carries the identity the launch set.
8. Run one recall. That route belongs to `WebFluxSecurity`. Assert the hits and
   `indexComplete`.

### The endpoint is synthetic and the client is production code

The launch points its base URL at a local process that answers the OpenAI
embeddings API with fixed vectors. `OpenAiEmbeddingModel` builds, serializes a
request, reads a response, and returns vectors.

**Production client code runs against a synthetic endpoint.** The gate needs no
secret key and no external network.

Project policy requires a key value, which the section above states. So the
launch supplies a dummy that is not a secret, and the stub ignores it.

This rests on the feature itself. The provider exists to serve any
OpenAI-compatible endpoint, so a compatible endpoint is a valid subject.

**Two checks stay manual and out of the gate.** A run against a real OpenAI
endpoint needs a key. A run of `chat-embedding-local` needs an ONNX model and a
tokenizer, which are large files that this repository does not carry. Each gets
a recorded procedure and neither runs unattended.

## Security Prerequisite

**`CHAT-jdsamcia` must land first.** It gives the actuator chain and the
application chain disjoint ownership, and the gate above depends on that split.
Its matcher must be management aware, because an operator can move the actuator
prefix with `management.endpoints.web.base-path`.

It also corrects `docs/VECTOR-RECALL-API.md`. Four of that document's curl
commands pass a chat user, and three pass the actuator user. The three actuator
commands stay correct.

One of the four chat user commands seeds through `POST /message/send/{id}`,
which binds a `ChatUserDetails` principal that no application chain supplies.
The other three call recall routes, which bind no principal. That issue changes
the seed to `PUT /persist/message/add` and drops the credentials from all four.

A persistence write creates no vector. So that issue also adds
`app.controller.persistence` to the document's property list, and it adds a
rebuild trigger and a poll before the first search.

## Build Prerequisite

**Neither provider resolves offline today.** A read on 2026-09-13 found only
metadata under `~/.m2` for `spring-ai-openai` and `spring-ai-transformers`. No
jar exists at any version.

One online build populates the cache. CI needs the same, and the dependency
audit workflow sees new artifacts.

## Failure Behavior

**An unreachable endpoint fails at a different moment for each vector store.**
`EmbeddingModel.dimensions()` is the reason. Spring AI can reach the remote
service to answer it.

| Vector store | When it calls `dimensions()` | Failure moment |
|--------------|------------------------------|----------------|
| `embedded` | when the collection bean builds | startup, always |
| `redis` | when it creates an index that is absent | startup, or later |
| `simple` | never, at build time | the first vector operation |

**Redis fails at startup only on a first run.** Schema initialization skips
`dimensions()` when the index already exists. A restart against an existing
index therefore starts cleanly, and the endpoint failure appears at the next
vector operation instead.

A new identity creates a new index name, so the first run under a new identity
does reach `dimensions()`.

So an operator sees a failed startup on `embedded` always, on `redis` only when
the index is new, and never on `simple`. Each message names the endpoint.

An absent ONNX model file fails startup, because the model loads when the bean
builds.

A production embedding with no identity fails startup.

A `mock` embedding with an identity fails startup.

An identity outside the character set fails startup.

A job written before this change never covers, because it carries null.

## Verification

- Each provider module supplies an `EmbeddingModel` behind its selector value.
- Each provider module supplies no bean when the selector names another value.
- Neither module depends on a Spring AI starter.
- A production embedding without an identity fails startup.
- A `mock` embedding with an identity fails startup.
- An identity with an illegal character fails startup.
- A `mock` embedding resolves the identity `mock`.
- **A deployment with no vector selector resolves no identity and still starts.**
- The redis index name and prefix carry the identity.
- The embedded collection directory carries the identity.
- The local cache directory carries the identity, under the configured base.
- A successful `IndexJob` records the identity of the model that wrote it.
- A job of another identity does not cover.
- A job written before this change does not cover.
- **A newer job of a foreign identity does not hide a current covering job.**
- The release sweep releases a stale job of another identity.
- The actuator read returns a job of another identity.
- An unreachable endpoint fails startup under `embedded`.
- An unreachable endpoint fails startup under `redis` when the index is new.
- An unreachable endpoint fails the first vector operation under `simple`.
- A packaged deployment launches with no test output on its classpath.
- The gate seeds through persistence and never through `/message/send/{id}`.
- **Each actuator gate call carries Basic credentials, and one without them
  answers 401. The seed and the recall carry none, because `WebFluxSecurity`
  owns those routes after `CHAT-jdsamcia`.**
- That deployment embeds text through the production client, during the rebuild.
- That deployment rebuilds, and its job carries the identity.
- That deployment completes one search.

## Out Of Scope

- A migration from an unnamed corpus. A new identity means a new corpus.
- Any deployment that sets `app.service.core.vector` by default.
- A second loading mode for any vector store.
- The Spring AI 2.0 move, which `CHAT-ygllyglb` tracks.
- Automatic selection between two embedding providers at runtime.
- Any change to `MessageRecallResult` or to either transport.
- The test scope cleanup of the other nine modules, which is a prerequisite.
- The security chain split, which `CHAT-jdsamcia` owns as a prerequisite. This
  work states the contract it needs and changes no security code.

## Decisions

1. Two production modules, one selector value each.
2. Each module depends on a model library and not on a starter.
3. Each module constructs one model, behind a condition.
4. The mock stays test only, in the `chat-core` test jar.
5. Both deployments declare both production modules at compile scope.
6. `chat-deploy-memory` moves its test jar dependency to test scope.
7. The operator supplies an identity, and the code never derives one.
8. A `mock` embedding resolves the fixed identity `mock`.
9. `chat-core` declares a typed `EmbeddingIdentity` and one resolver bean.
10. Five call sites inject the resolved value, and none reads the property.
11. `IndexJob.embeddingIdentity` is nullable, so a legacy record states that it
    named no model.
12. Coverage selection is the only place that filters on identity.
13. The identity filter runs before the sort and before `next()`.
14. Ten legal selector pairs, and no mock store takes a production model.
15. The local module sets a cache directory that carries the identity, under a
    configurable base that defaults to a temporary directory.
16. One guard holds two rules, and rule two closes `CHAT-incuynpc`.
17. Nine unscoped test jar dependencies move in a prerequisite issue. This work
    moves the tenth, and then installs the guard.
18. The resolver bean is absent when no vector selector is set.
19. The gate builds with `expose-webflux`, seeds through persistence, rebuilds
    through the actuator, and runs production client code against a synthetic
    endpoint.
20. The OpenAI key is required by this design rather than by Spring AI.
21. A real endpoint and a real ONNX model stay manual.
22. The two filter chains take disjoint ownership in `CHAT-jdsamcia`, which is a
    prerequisite. The owner chose that over dropping `expose-webflux` and over a
    gate that asserts the current behaviour.

## Decisions The Owner Has Not Made

These are my choices inside the shape the owner set. Each one is open to
reversal.

- The identity property name and its character set.
- The typed value class, in place of a plain string.
- `expose-webflux` for the gate, in place of an RSocket client.
- An identity-specific cache directory, in place of disabled caching.
- The order of the gate steps after the rebuild.
