# Embedding Provider Modules

Status: proposed. The owner chose this shape. The document waits for review.

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

Every production model carries an operator-supplied identity. That identity
reaches the store names and the durable job record.

A guard refuses a test artifact on a production classpath.

The final proof launches a packaged deployment with no test output on its
classpath. That deployment creates an embedding and completes one search.

## The Two Providers

### chat-embedding-openai

The module owns three values. The operator sets all three.

- `app.service.core.embedding.openai.base-url`
- `app.service.core.embedding.openai.api-key`
- `app.service.core.embedding.openai.model`

Spring AI supports an OpenAI-compatible endpoint through a base URL. So this
provider serves OpenAI and any service that speaks the same API.

### chat-embedding-local

The module owns two values. The operator sets both.

- `app.service.core.embedding.local.model-uri`
- `app.service.core.embedding.local.tokenizer-uri`

Spring AI reads `file:`, `classpath:`, and `https:` resources. So an operator
supplies a model from disk, from the artifact, or from a remote host.

**This module builds against Spring AI 1.0.3.** The root pom pins that version
and forbids 2.0.0. The ONNX documentation for 2.0 describes a different surface,
so the implementation follows the 1.0 surface of `spring-ai-transformers`.

### Neither provider changes a vector store

A vector store receives an `EmbeddingModel` and asks nothing about its origin.
The embedded store also reads `dimensions()` from it. No store needs a second
loading mode.

## Model Identity

**Every production model carries an identity that the operator sets.**

```properties
app.service.core.embedding.identity=acme-e5-small-v2
```

The value is required when `embedding` is `openai` or `local`. It is refused
when `embedding` is `mock`, which carries the fixed identity `mock`.

The value must match `[a-z0-9][a-z0-9-]{0,63}`. A redis key prefix and a
directory name both carry it, so the character set is narrow. Startup fails on
any other value.

**The identity is not derived.** A base URL and a model name do not identify the
output of a remote service. A compatible service can change its model behind
both values and return different vectors for the same text. Only an operator
knows that a change happened, so only an operator can name the identity.

**The identity is immutable for a corpus.** A new identity means a new corpus.
It does not mean a migration.

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

### The durable job record

`IndexJob` gains one field.

```kotlin
val embeddingIdentity: String = ""
```

The default is the empty string, so a record written before this change still
decodes. That record then carries an identity that no valid value can equal.

### Coverage selection

`VectorCoveragePolicyImpl` adds one filter. A job covers only when its
`embeddingIdentity` equals the identity this process runs.

**This change invalidates every covering job that exists today.** Each one
carries the empty identity, and the empty string is not a legal identity. So the
first start after this change reports an incomplete index and waits for a
rebuild. That outcome is correct, because those vectors came from a model that
no longer has a name.

### Where identity is not used

**The release sweep does not filter on identity.** It marks the stale running
jobs of earlier incarnations as released. A stale job is stale whatever model
wrote it, and releasing it is correct in every case.

**The actuator read does not filter on identity.** An operator needs to see the
jobs of every model this node wrote. The job list carries the identity field, so
the reader can tell them apart.

## Selector Pairs

`VectorSelectorValidation` holds the legal pairs. The set grows from four to
twelve.

| vector | embedding |
|--------|-----------|
| `mock`, `simple`, `redis`, `embedded` | `mock` |
| `mock`, `simple`, `redis`, `embedded` | `openai` |
| `mock`, `simple`, `redis`, `embedded` | `local` |

Every vector store accepts every embedding. A store receives a model and asks
nothing about it.

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

**`chat-deploy-memory` also loses its compile-scope test jar.** That dependency
gains `<scope>test</scope>`, which matches every other module. The mock then
reaches tests and never reaches a launch.

A deployment can select only an embedding it declares, exactly as it can select
only a vector store it declares.

## The Production Dependency Guard

One script holds two rules. Both run over every module.

**Rule one reads the poms.** Every dependency with `<type>test-jar</type>` must
declare `<scope>test</scope>`.

**Rule two reads the resolved classpath.** A known test library must not appear
on a compile or runtime classpath. The rule reads
`dependency:build-classpath` with runtime scope, because a transitive leak does
not appear in a pom.

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

The guard joins `just check-deps` beside `check-dependency-versions.sh`.

## The Packaged Launch Proof

**A test classpath cannot prove this feature works.** Every gate in this
repository puts test outputs on the classpath, which is what hid the defect. So
the proof runs outside a test classpath.

The proof builds the deployment jar, launches it with `java -jar`, and drives it
over HTTP.

1. Build `chat-deploy-memory` and repackage it.
2. Assert that no test output is on the launched classpath.
3. Launch with `embedding=openai`, `vector=simple`, and an identity.
4. Send one message, which creates one embedding.
5. Run one recall, which completes one search.
6. Read `/actuator/vectorindex`, and assert the job carries the identity.

**The endpoint is a stub, and the model is real.** The launch points its base URL
at a local process that answers the OpenAI embeddings API with fixed vectors.
`OpenAiEmbeddingModel` builds, serializes a request, and reads a response. So
the provider path is exercised, and the gate needs no key and no network.

This choice rests on the feature itself. The provider exists to serve any
OpenAI-compatible endpoint, so a compatible endpoint is a valid subject.

**Two checks stay manual and out of the gate.** A run against a real OpenAI
endpoint needs a key. A run of `chat-embedding-local` needs an ONNX model and a
tokenizer, which are large files that this repository does not carry. Each gets
a recorded procedure and neither runs unattended.

## Build Prerequisite

**Neither provider resolves offline today.** A read on 2026-09-13 found only
metadata under `~/.m2` for `spring-ai-openai`, `spring-ai-transformers`, and
both starters. No jar exists at any version.

One online build populates the cache. CI needs the same, and the dependency
audit workflow sees new artifacts.

## Failure Behavior

A production embedding with no identity fails startup.

A `mock` embedding with an identity fails startup.

An identity outside the character set fails startup.

An unreachable OpenAI endpoint fails the first embedding, and the live add path
removes coverage. The failure reaches the sender, which is the existing rule.

An absent ONNX model file fails startup, because the model loads when the bean
builds.

A job written before this change never covers, because it carries no identity.

## Verification

- Each provider module supplies an `EmbeddingModel` behind its selector value.
- Each provider module supplies no bean when its selector names another value.
- A production embedding without an identity fails startup.
- A `mock` embedding with an identity fails startup.
- An identity with an illegal character fails startup.
- The redis index name and prefix carry the identity.
- The embedded collection directory carries the identity.
- A successful job records the identity of the model that wrote it.
- A job of another identity does not cover.
- A job written before this change does not cover.
- The release sweep releases a stale job of another identity.
- The actuator read returns a job of another identity.
- Every test jar dependency declares test scope.
- No known test library resolves on a runtime classpath.
- A packaged deployment launches with no test output on its classpath.
- That deployment creates an embedding and completes one search.
- That deployment records the identity on its job.

## Out Of Scope

- A migration from an unnamed corpus. A new identity means a new corpus.
- Any deployment that sets `app.service.core.vector` by default.
- A second loading mode for any vector store.
- The Spring AI 2.0 move, which `CHAT-ygllyglb` tracks.
- Automatic selection between two embedding providers at runtime.
- Any change to `MessageRecallResult` or to either transport.

## Decisions

1. Two production modules, one selector value each.
2. The mock stays test only, in the `chat-core` test jar.
3. Both deployments declare both production modules at compile scope.
4. `chat-deploy-memory` moves its test jar dependency to test scope.
5. The operator supplies an identity, and the code never derives one.
6. A `mock` embedding carries the fixed identity `mock`.
7. The identity reaches redis names, the embedded namespace, and the job record.
8. Coverage selection is the only place that filters on identity.
9. `IndexJob.embeddingIdentity` defaults to the empty string, so old records
   decode and never cover.
10. One guard holds two rules, and rule two closes `CHAT-incuynpc`.
11. The gate proves the feature from a packaged jar, against a stub endpoint.
12. A real endpoint and a real ONNX model stay manual.

## Decisions The Owner Has Not Made

These are my choices inside the shape the owner set. Each one is open to reversal.

- The identity property name and its character set.
- The fixed `mock` identity, in place of a nullable field.
- The empty string default on `IndexJob`, in place of a nullable field.
- The release sweep and the actuator read ignoring identity.
- The stub endpoint as the gate, in place of a real endpoint or a real model.
- The twelve legal pairs, in place of a narrower set.
