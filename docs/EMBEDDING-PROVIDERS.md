# Embedding providers

How to give a deployment a real embedding model, and what each choice costs.

Every claim here names the test or the gate that proves it. A claim with no
proof beside it is marked as unproven.

## The three embedding values

`app.service.core.embedding` takes one of three values.

| Value | What supplies the model | Where it lives |
|-------|-------------------------|----------------|
| `mock` | `DummyEmbeddingModel`, character bigrams at 256 dimensions | the `chat-core` test jar, at test scope |
| `openai` | `OpenAiEmbeddingModel`, against any OpenAI-compatible endpoint | `chat-embedding-openai` |
| `local` | `TransformersEmbeddingModel`, an ONNX model in this process | `chat-embedding-local` |

**The mock is test only.** It reaches a test classpath and it reaches no
launch. `chat-deploy-memory` once declared that test jar with no scope element,
so the mock resolved at compile and the feature ran on a mock in production.
No test could detect that, because a Spring Boot test puts test jars on its
classpath by construction. `just check-production-classpath` now holds the
rule.

## The ten legal selector pairs

`app.service.core.vector` and `app.service.core.embedding` are set together. An
illegal pair fails the startup and the message lists every legal pair.

| Vector store | `mock` | `openai` | `local` |
|--------------|--------|----------|---------|
| `mock` | legal | refused | refused |
| `simple` | legal | legal | legal |
| `redis` | legal | legal | legal |
| `embedded` | legal | legal | legal |

**A mock vector store refuses a production model.** A mock store holds no
vectors that anyone reads, so a paid or loaded model would do work that nothing
uses.

`VectorSelectorValidationTests` pins the ten pairs and the four refusals.

## The identity

```properties
app.service.core.embedding.identity=acme-e5-small-v2
```

**The operator names it. The code never derives one.** A base URL and a model
name do not identify the output of a remote service, because a compatible
service can change its model behind both values and return different vectors
for the same text. Only an operator knows that a change happened.

Three rules, each with its own failure.

1. A production embedding with no identity fails the startup.
2. A `mock` embedding with an identity fails the startup. The mock resolves the
   fixed identity `mock`.
3. An identity that does not match `[a-z0-9][a-z0-9-]{0,63}` fails the startup.
   A redis key prefix and a directory name both carry the value, so the
   character set is narrow.

**A new identity means a new corpus. It does not mean a migration.** Nothing
reads the vectors of the old identity again, and nothing deletes them either.

`EmbeddingIdentityTests` pins the three rules.

## The openai provider

```properties
app.service.core.embedding=openai
app.service.core.embedding.openai.base-url=https://api.openai.com
app.service.core.embedding.openai.api-key=<the key>
app.service.core.embedding.openai.model=text-embedding-3-small
app.service.core.embedding.openai.max-attempts=10
```

`base-url`, `api-key`, and `model` are each required, and a blank value fails
the startup the same way an absent one does. A blank key reaches a remote service as an anonymous call,
and an operator cannot tell a missing key from an intended one.

`max-attempts` is optional, and it counts calls. An unset value gives 10 calls.
Set a lower number when a caller cannot wait for ten.

**The OpenAI SDK owns the retry since Spring AI 2.0.** The SDK counts retries
after the first call, so the module subtracts one before it passes the value.
An unset property sets 9 retries, which keeps the 10 calls that Spring AI 1.0.3
made. **The SDK default is 2 retries, and the module never uses it**, because
three calls would replace ten without any report.

The wait between calls now belongs to the SDK. The earlier figure of 1142
seconds described the Spring AI 1.0.3 template, and it no longer applies. **No
measurement of the SDK wait exists yet.** See CHAT-chsvdqbi.

The module depends on `spring-ai-openai` and not on a starter. A starter
carries auto-configuration, both provider modules sit on one classpath, and two
starters would let Spring AI build models that no selector asked for.

## The local provider

```properties
app.service.core.embedding=local
app.service.core.embedding.local.model-uri=file:/opt/models/model.onnx
app.service.core.embedding.local.tokenizer-uri=file:/opt/models/tokenizer.json
app.service.core.embedding.local.cache-path=/var/cache/chat-embedding-local
```

Both URIs are required, and a blank value fails the startup. Spring AI reads
`file:`, `classpath:`, and `https:` resources.

`cache-path` is optional. Its default is `chat-embedding-local` under the
system temporary directory, which is ephemeral. **The cache directory carries
the identity as its last segment.** `ResourceCacheService` caches a remote
resource by its location, so a new identity with unchanged URIs would otherwise
load the old bytes and write the new corpus with the old model.

The model loads while the bean builds, so an absent file fails the startup.

## What orphans

Nothing migrates. Each item below stays on disk and no read reaches it again.

| Item | Why it orphans |
|------|----------------|
| An existing redis index | The name is now `chat:vector:<keyType>:<identity>:message`, and an older index has no identity segment |
| An existing embedded directory | The path now ends with the identity, and an older directory does not |
| An existing job record | The record carries a null identity, and the coverage filter matches on the identity |

**So the first start after this change reports an incomplete index and waits
for a rebuild.** A null identity names no model, which is the correct reading
of a record that a previous build wrote.

## The failure moment of an unreachable endpoint

Each vector store asks `EmbeddingModel.dimensions()` at a different time, so
each one fails at a different moment.

| Vector store | When it asks | Failure moment |
|--------------|--------------|----------------|
| `embedded` | when the collection bean builds | startup, always |
| `redis` | when it creates an index that is absent | startup, or later |
| `simple` | never, at build time | the first vector operation |

`DeadEmbeddingEndpointTests` and `RedisDeadEmbeddingEndpointTests` pin all
three.

## Manual procedure one: a real OpenAI endpoint

**This procedure needs a real key and network access, and it never runs
unattended.** No automated gate in this repository calls a paid service.

Build the executable artifact first. The `deploy` profile is what repackages
it, and the `exec` classifier is what names it.

```bash
mvn -o -B -Pexpose-webflux,deploy -Dmaven.test.skip=true \
    -pl chat-deploy-memory -am clean package
```

```bash
java --enable-native-access=ALL-UNNAMED -jar chat-deploy-memory/target/chat-deploy-memory-0.0.1-exec.jar \
    --app.nodeid=1 \
    --app.key.type=long \
    --app.server.proto=rest \
    --server.port=8080 \
    --app.service.core.key=memory \
    --app.service.core.persistence=memory \
    --app.service.core.index=lucene \
    --app.service.core.pubsub=memory \
    --app.service.core.secrets=memory \
    --app.service.composite=true \
    --app.service.composite.auth=true \
    --app.service.security.userdetails=true \
    --app.service.core.vector=simple \
    --app.service.core.embedding=openai \
    --app.service.core.embedding.identity=text-embedding-3-small-1536 \
    --app.service.core.embedding.openai.base-url=https://api.openai.com \
    --app.service.core.embedding.openai.api-key="$OPENAI_API_KEY" \
    --app.service.core.embedding.openai.model=text-embedding-3-small \
    --app.controller.persistence=true \
    --app.controller.recall=true \
    --app.actuator.username=actuator \
    --app.actuator.password=actuator \
    --management.endpoint.vectorindex.enabled=true \
    --management.endpoints.web.exposure.include=vectorindex,health
```

Then follow `docs/VECTOR-RECALL-API.md`: seed through `PUT
/persist/message/add`, trigger a rebuild through `POST /actuator/vectorindex`,
poll until `running` is false, and search.

**Put the dimension in the identity.** One service can serve several widths of
one model, and two corpora of different widths must not share one name.

A deployment that runs against an OpenAI-compatible service on a private host
reads the same way. Give `base-url` that host, and give `model` the name that
host serves.

### The house endpoint

One OpenAI-compatible endpoint runs on the local network. Every value below was
measured against it on 2026-09-15.

| Fact | Value |
|---|---|
| Host | `node2.lan`, port 6090, plain HTTP |
| Model name | `nomic-embed-text-v1.5.Q8_0.gguf` |
| Server | llama.cpp |
| Width | 768 |

```properties
app.service.core.embedding=openai
app.service.core.embedding.openai.base-url=http://node2.lan:6090
app.service.core.embedding.openai.api-key=not-a-secret
app.service.core.embedding.openai.model=nomic-embed-text-v1.5.Q8_0.gguf
app.service.core.embedding.identity=nomic-embed-text-v1-5-768
```

**Give `base-url` the host and the port, and nothing else.** The client appends
`/v1/embeddings` itself. A base URL that already ends in `/v1` makes the client
call `/v1/v1/embeddings`, and that path answered 404.

**This endpoint reads no key.** Project policy requires a value, so the launch
supplies one that is not a secret.

**The endpoint serves 768 and only 768 today.** A request that carried
`"dimensions": 512`, `256`, or `64` answered with 768 floats each time. The
model supports a narrower width through truncation, and this provider does not
truncate. So the identity names 768.

**Put the width in the identity.** A later change to a narrower width is a new
corpus, and it must carry a new name.

## Manual procedure two: a real ONNX model

**This repository carries neither file.** Download both, and check what
arrives.

`all-MiniLM-L6-v2` emits 384 dimensions. At revision
`1110a243fdf4706b3f48f1d95db1a4f5529b4d41`:

| File | Bytes | sha256 |
|---|---|---|
| `onnx/model.onnx` | 90405214 | `6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452` |
| `tokenizer.json` | 466247 | `be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037` |

```bash
REV=1110a243fdf4706b3f48f1d95db1a4f5529b4d41
BASE="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/$REV"
CACHE="$HOME/.cache/chat-embedding-local-model"
mkdir -p "$CACHE"
curl -sSfL -o "$CACHE/model.onnx"     "$BASE/onnx/model.onnx"
curl -sSfL -o "$CACHE/tokenizer.json" "$BASE/tokenizer.json"
```

**Pin the revision.** A `main` URL serves whatever that branch holds today, and
the identity states which model wrote a corpus. A mutable URL under a fixed
identity lets two different models share one name.

Launch with the local selector.

```bash
    --app.service.core.vector=simple \
    --app.service.core.embedding=local \
    --app.service.core.embedding.identity=minilm-l6-v2-384 \
    --app.service.core.embedding.local.model-uri="file:$HOME/.cache/chat-embedding-local-model/model.onnx" \
    --app.service.core.embedding.local.tokenizer-uri="file:$HOME/.cache/chat-embedding-local-model/tokenizer.json"
```

`LocalEmbeddingModelTests` runs the same model under two opt in properties.

```bash
mvn -o -pl chat-core,chat-embedding-local -Pintegration \
    -DargLine="-Dchat.embedding.local.manual=true -Dchat.embedding.local.remote=true" \
    -Dtest=LocalEmbeddingModelTests -Dsurefire.failIfNoSpecifiedTests=false clean verify
```

**Without `chat.embedding.local.manual` every test there is skipped**, whatever
sits in the cache directory. A gate on the downloaded files alone would load a
90 MiB model in every later integration build on that machine. The remote test
carries a second property, because it downloads two more file sets.

## The guard

```bash
just check-production-classpath
```

Two rules, and neither replaces the other.

**Rule one reads the poms.** Every dependency with `<type>test-jar</type>` must
declare `<scope>test</scope>`.

**Rule two reads the resolved runtime classpath of each module.** A known test
library must not appear on it.

A transitive leak never appears in a pom, so rule one cannot see it. An
ordinary jar such as `testcontainers` or `spring-boot-starter-test` is not a
test jar, so rule one cannot see that either. Rule two resolves rather than
reads, which is why it catches both.

## The packaged launch gate

```bash
./shell-scripts/vector/gate-embedding-launch.sh
```

**A test classpath cannot prove this feature works.** Every test gate in this
repository puts test outputs on the classpath, which is what hid the original
defect. So this gate builds a packaged deployment, asserts that no test output
is inside the artifact, launches it against a synthetic OpenAI endpoint, seeds
three messages, rebuilds the index, and runs one search.

The gate needs no external network and no secret key. Production client code
runs against a synthetic endpoint, and only the service is synthetic.
