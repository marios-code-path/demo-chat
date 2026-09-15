# Embedding Provider Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to
> implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking. This repository forbids subagent-driven development. See
> `CLAUDE.md`.

**Goal:** Give every vector deployment a production `EmbeddingModel`, a named
model identity, and a guard that keeps test artifacts off a production
classpath.

**Architecture:** Two new modules each supply one `EmbeddingModel` behind one
selector value. `chat-core` declares a typed `EmbeddingIdentity` and one
resolver bean. Five call sites inject the resolved value, so the identity
reaches the redis index name, the embedded directory, the local cache
directory, and every `IndexJob`. A shell guard reads the poms and the resolved
classpaths. A packaged launch proves the feature outside a test classpath.

**Tech Stack:** Kotlin 2.4.10, Java 25, Spring Boot 3.5.16, Spring AI 1.0.3,
Project Reactor, JUnit 5, AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-09-13-embedding-provider-modules-design.md`

**Issue:** `CHAT-etfnihnu`

## Global Constraints

- Write all prose in ASD-STE100 strict mode. See `CLAUDE.md`.
- Spring AI stays at 1.0.3. The root pom pins it. Do not use 2.0.0.
- No module pom declares a third-party version. The parent manages every
  version. `shell-scripts/check-dependency-versions.sh` fails on a breach.
- `chat-core` does not enable the Kotlin all-open compiler plugin. A
  `@Configuration` class there must be `open`. Its `@Bean` methods must be
  `open`. The vector modules do enable the plugin, so their classes need no
  `open`.
- The identity value must match `[a-z0-9][a-z0-9-]{0,63}`.
- Run `mvn -o -pl chat-core,<module> test`. Never run `-pl <module>` alone. A
  single-module run reads a stale `chat-core` from `~/.m2`.
- Run `drift check` and `git diff --check` before each commit.
- Activate miniforge before you run Python. Run
  `source ~/miniforge3/etc/profile.d/conda.sh && conda activate base`. Ask the
  owner for guidance when `conda` is absent. See `CLAUDE.md`.
- Never pipe a build or a gate into `tail`. The pipeline reports the exit
  status of `tail`, which hides a failure. Redirect to a file, test the status,
  and then read the file.
- Mention `CHAT-etfnihnu` in each commit message.
- Prefix every `fp` command with `FP_AGENT_NAME='sigma'`. This repository sets
  that convention. See `docs/superpowers/plans/2026-09-10-vector-reindex.md`,
  line 29.
- End each commit message with
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

## Prerequisites

Two issues must close. Each gates a different task, and neither gates Task 1.

- `CHAT-xvtsffqh` moves nine test jar dependencies to test scope. **Task 7**
  needs it, because rule one of the guard fails until those nine move. That
  issue owns nine modules. This work owns the tenth, `chat-deploy-memory`, in
  Task 6. Neither issue owns a module of the other, so neither deadlocks.
- `CHAT-jdsamcia` gives the actuator chain and the application chain disjoint
  ownership. **Task 9** needs it, because the packaged gate calls actuator
  routes under credentials and application routes without them.

`CHAT-incuynpc` is not a separate prerequisite. `CHAT-xvtsffqh` carries the two
`chat-shell` testcontainers declarations that close it. Rule two of the Task 7
guard is what keeps that breach from returning, because those are ordinary jars
and rule one cannot see them.

One build prerequisite has no issue. Neither `spring-ai-openai` nor
`spring-ai-transformers` resolves offline today. A read on 2026-09-13 found
only metadata under `~/.m2`. Task 2 runs one online build to populate the
cache.

---

## File Structure

### New files

| File | Responsibility |
|------|----------------|
| `chat-core/src/main/kotlin/com/demo/chat/domain/EmbeddingIdentity.kt` | The typed identity value and its character rule |
| `chat-core/src/main/kotlin/com/demo/chat/config/EmbeddingIdentityConfiguration.kt` | The one resolver bean |
| `chat-core/src/test/kotlin/com/demo/chat/test/config/EmbeddingIdentityTests.kt` | Resolver rules |
| `chat-embedding-openai/pom.xml` | The OpenAI provider module |
| `chat-embedding-openai/src/main/kotlin/com/demo/chat/config/embedding/openai/OpenAiEmbeddingConfiguration.kt` | Builds one `OpenAiEmbeddingModel` behind the selector |
| `chat-embedding-openai/src/test/kotlin/com/demo/chat/test/embedding/openai/OpenAiEmbeddingConfigurationTests.kt` | Selector and property rules |
| `chat-embedding-local/pom.xml` | The local provider module |
| `chat-embedding-local/src/main/kotlin/com/demo/chat/config/embedding/local/LocalEmbeddingConfiguration.kt` | Builds one `TransformersEmbeddingModel` behind the selector |
| `chat-embedding-local/src/test/kotlin/com/demo/chat/test/embedding/local/LocalEmbeddingConfigurationTests.kt` | Selector, cache path, and identity rules |
| `shell-scripts/check-production-classpath.sh` | The two-rule guard |
| `shell-scripts/vector/gate-embedding-launch.sh` | The packaged launch gate |
| `shell-scripts/vector/openai-stub-server.py` | The synthetic embeddings endpoint |
| `chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorNamesTests.kt` | The redis name rules |
| `chat-vector-embedded/src/test/kotlin/com/demo/chat/test/vector/embedded/EmbeddedStorageDirectoryTests.kt` | The embedded directory rules, and the wiring proof |
| `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/DeadEmbeddingEndpointTests.kt` | The `embedded` and `simple` failure moments |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisDeadEmbeddingEndpointTests.kt` | The `redis` failure moment |
| `docs/EMBEDDING-PROVIDERS.md` | Operator document, and the two manual procedures |

### Modified files

| File | Change |
|------|--------|
| `pom.xml` | Two new `<module>` entries |
| `chat-core/src/main/kotlin/com/demo/chat/config/VectorSelectorValidation.kt` | Ten legal pairs, and the identity rules |
| `chat-core/src/test/kotlin/com/demo/chat/test/config/VectorSelectorValidationTests.kt` | The ten pairs, and the new failures |
| `chat-core/src/main/kotlin/com/demo/chat/domain/IndexJob.kt` | One new nullable field |
| `chat-vector-redis/src/main/kotlin/com/demo/chat/config/vector/redis/RedisVectorStoreConfiguration.kt` | Identity in the index name and the prefix |
| `chat-vector-embedded/pom.xml` | A compile scope `chat-core` dependency |
| `chat-vector-embedded/src/main/kotlin/com/demo/chat/config/vector/embedded/EmbeddedVectorStoreConfiguration.kt` | Identity in the storage directory |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorIndexJobStoreImpl.kt` | Writes the identity on a new job |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorCoveragePolicyImpl.kt` | Filters on the identity |
| `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt` | Passes the identity to both |
| `chat-core/src/test/kotlin/com/demo/chat/test/vector/IndexJobCodecTests.kt` | Two legacy decode tests |
| `chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorStoreConfigurationTests.kt` | The identity bean, and the redis wiring proof |
| `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt` | The identity bean |
| `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorCoveragePolicyImplTests.kt` | The identity filter tests |
| `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorIndexJobStoreImplTests.kt` | The identity write test |
| `chat-deploy-memory/pom.xml` | Both provider modules, and the test jar scope move |
| `chat-deploy-redis/pom.xml` | Both provider modules |
| `justfile` | One recipe for the guard |
| `docs/BUILD-HEALTH.md` | The new module count and the new test counts |
| `forward-register.md` | One section for this work |

---

## Task 1: The typed identity and its rules

This task adds the identity type, the resolver bean, and the selector rules. It
adds no provider. A later task adds each provider.

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/EmbeddingIdentity.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/config/EmbeddingIdentityConfiguration.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/config/EmbeddingIdentityTests.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/config/VectorSelectorValidation.kt`
- Modify: `chat-core/src/test/kotlin/com/demo/chat/test/config/VectorSelectorValidationTests.kt`

**Interfaces:**
- Consumes: nothing from an earlier task.
- Produces:
  - `com.demo.chat.domain.EmbeddingIdentity`, a `data class` over `String`.
    It is not a `@JvmInline value class`. Kotlin unboxes a value class at a
    return type, so a bean method would supply a `String` bean.
  - `EmbeddingIdentity.Companion.PATTERN: Regex`.
  - `EmbeddingIdentity.Companion.MOCK: EmbeddingIdentity`.
  - `EmbeddingIdentity.Companion.of(embedding: String, identity: String?): EmbeddingIdentity`.
  - A bean of type `EmbeddingIdentity`, named `embeddingIdentity`. It is absent
    when `app.service.core.vector` and `app.service.core.embedding` are absent.
  - `VectorSelectorValidation.validate(vector: String?, embedding: String?, identity: String?)`.
    The signature gains one parameter.

- [ ] **Step 1: Write the failing test for the identity type**

Create `chat-core/src/test/kotlin/com/demo/chat/test/config/EmbeddingIdentityTests.kt`.

```kotlin
package com.demo.chat.test.config

import com.demo.chat.config.EmbeddingIdentityConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class EmbeddingIdentityTests {

    @Test
    fun `a mock embedding resolves the fixed mock identity`() {
        assertThat(EmbeddingIdentity.of("mock", null)).isEqualTo(EmbeddingIdentity.MOCK)
    }

    @Test
    fun `a mock embedding with an identity fails`() {
        assertThatThrownBy { EmbeddingIdentity.of("mock", "acme-e5") }
            .hasMessageContaining("app.service.core.embedding.identity")
            .hasMessageContaining("mock")
    }

    @Test
    fun `a production embedding without an identity fails`() {
        assertThatThrownBy { EmbeddingIdentity.of("openai", null) }
            .hasMessageContaining("app.service.core.embedding.identity")
            .hasMessageContaining("openai")
    }

    @Test
    fun `a production embedding with a blank identity fails`() {
        assertThatThrownBy { EmbeddingIdentity.of("local", "   ") }
            .hasMessageContaining("app.service.core.embedding.identity")
    }

    @Test
    fun `an identity outside the character set fails`() {
        for (illegal in listOf("Acme", "acme_e5", "acme.e5", "-acme", "acme e5", "a".repeat(65))) {
            assertThatThrownBy { EmbeddingIdentity.of("openai", illegal) }
                .describedAs("expected a failure for '%s'", illegal)
                .hasMessageContaining("app.service.core.embedding.identity")
        }
    }

    @Test
    fun `a legal identity resolves to its own value`() {
        for (legal in listOf("a", "acme-e5-small-v2", "0", "a".repeat(64))) {
            assertThat(EmbeddingIdentity.of("openai", legal).value).isEqualTo(legal)
        }
    }

    @Test
    fun `the bean is absent when both selectors are absent`() {
        runner(emptyMap()).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(EmbeddingIdentity::class.java)
        }
    }

    @Test
    fun `the bean is present when both selectors are set`() {
        runner(
            mapOf(
                "app.service.core.vector" to "simple",
                "app.service.core.embedding" to "mock",
            )
        ).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(EmbeddingIdentity::class.java))
                .isEqualTo(EmbeddingIdentity.MOCK)
        }
    }

    @Test
    fun `the bean carries the operator value for a production embedding`() {
        runner(
            mapOf(
                "app.service.core.vector" to "simple",
                "app.service.core.embedding" to "openai",
                "app.service.core.embedding.identity" to "acme-e5-small-v2",
            )
        ).run { context ->
            assertThat(context.getBean(EmbeddingIdentity::class.java).value)
                .isEqualTo("acme-e5-small-v2")
        }
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withUserConfiguration(EmbeddingIdentityConfiguration::class.java)
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
mvn -o -pl chat-core -Dtest=EmbeddingIdentityTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: a compile failure. `EmbeddingIdentity` does not exist.

- [ ] **Step 3: Write the identity type**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/EmbeddingIdentity.kt`.

```kotlin
package com.demo.chat.domain

/**
 * The operator's name for the model that writes a corpus.
 *
 * The value is never derived. A base URL and a model name do not identify the
 * output of a remote service. A compatible service can change its model behind
 * both values and return different vectors for the same text. Only an operator
 * knows that a change happened.
 *
 * The value is immutable for a corpus. A new identity means a new corpus, and
 * it does not mean a migration.
 *
 * The type exists to stop a raw string from reaching the wrong parameter.
 * Three call sites already take a keyType string, and a second string beside
 * it would be easy to swap.
 *
 * The type is a data class, and it is not a JvmInline value class. Kotlin
 * unboxes a value class at a return type, so a bean method would supply a
 * String bean. Spring must see this type.
 */
data class EmbeddingIdentity(val value: String) {

    companion object {
        /** A redis key prefix and a directory name both carry this value. */
        val PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}")

        const val MOCK_EMBEDDING = "mock"

        val MOCK = EmbeddingIdentity(MOCK_EMBEDDING)

        const val PROPERTY = "app.service.core.embedding.identity"

        /**
         * Applies the three rules and returns the resolved value.
         *
         * A mock embedding resolves the fixed identity and refuses an operator
         * value. A production embedding requires an operator value. Any value
         * must match [PATTERN].
         */
        fun of(embedding: String, identity: String?): EmbeddingIdentity {
            val given = identity?.takeIf { it.isNotBlank() }

            if (embedding == MOCK_EMBEDDING) {
                if (given != null) {
                    throw IllegalStateException(
                        "$PROPERTY=$given is set, and app.service.core.embedding=mock. " +
                            "The mock embedding resolves the fixed identity '$MOCK_EMBEDDING'. " +
                            "Remove $PROPERTY, or name a production embedding."
                    )
                }
                return MOCK
            }

            if (given == null) {
                throw IllegalStateException(
                    "$PROPERTY is not set, and app.service.core.embedding=$embedding. " +
                        "A production embedding needs an identity that this operator names. " +
                        "The value must match ${PATTERN.pattern}."
                )
            }

            if (!PATTERN.matches(given)) {
                throw IllegalStateException(
                    "$PROPERTY=$given does not match ${PATTERN.pattern}. " +
                        "A redis key prefix and a directory name both carry this value, " +
                        "so the character set is narrow."
                )
            }

            return EmbeddingIdentity(given)
        }
    }
}
```

- [ ] **Step 4: Write the resolver configuration**

Create `chat-core/src/main/kotlin/com/demo/chat/config/EmbeddingIdentityConfiguration.kt`.

```kotlin
package com.demo.chat.config

import com.demo.chat.domain.EmbeddingIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The one place that reads the identity property.
 *
 * Five call sites inject the resolved value, and none of them reads the
 * property. See the design document.
 *
 * The bean is absent when both vector selectors are absent. Most deployments
 * set neither selector today, and they must keep starting. So this class
 * carries the same condition the recall beans carry.
 *
 * The module does not enable the Kotlin all-open compiler plugin. A
 * configuration class must be open, like BaseDomainConfiguration.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
open class EmbeddingIdentityConfiguration(
    @Value("\${app.service.core.embedding}") embedding: String,
    @Value("\${app.service.core.embedding.identity:}") identity: String,
) {

    private val embeddingSelector = embedding
    private val identityValue = identity

    @Bean
    open fun embeddingIdentity(): EmbeddingIdentity =
        EmbeddingIdentity.of(embeddingSelector, identityValue)
}
```

- [ ] **Step 5: Run the test and confirm it passes**

```bash
mvn -o -pl chat-core -Dtest=EmbeddingIdentityTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, 9 tests.

- [ ] **Step 6: Write the failing test for the ten selector pairs**

Modify `chat-core/src/test/kotlin/com/demo/chat/test/config/VectorSelectorValidationTests.kt`.

Replace the `LEGAL_PAIRS` companion value with the ten pairs.

```kotlin
    private companion object {
        // Mirrors VectorSelectorValidation.legalPairs. Both must change together.
        val LEGAL_PAIRS = listOf(
            "mock" to "mock",
            "simple" to "mock",
            "redis" to "mock",
            "embedded" to "mock",
            "simple" to "openai",
            "redis" to "openai",
            "embedded" to "openai",
            "simple" to "local",
            "redis" to "local",
            "embedded" to "local",
        )
    }
```

Delete the test named `embedded vector with a reserved embedding fails startup`.
That pair is legal now. Add four tests in its place.

```kotlin
    @Test
    fun `a mock vector store refuses a production embedding`() {
        for (embedding in listOf("openai", "local")) {
            val failure = failureFor(
                mapOf(
                    "app.service.core.vector" to "mock",
                    "app.service.core.embedding" to embedding,
                    "app.service.core.embedding.identity" to "acme-e5",
                )
            )

            assertThat(failure.message)
                .describedAs("expected a failure for embedding=%s", embedding)
                .contains("app.service.core.vector=mock")
                .contains("app.service.core.embedding=$embedding")
        }
    }

    @Test
    fun `a production embedding without an identity fails startup`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.vector" to "simple",
                "app.service.core.embedding" to "openai",
            )
        )

        assertThat(failure.message).contains("app.service.core.embedding.identity")
    }

    @Test
    fun `a mock embedding with an identity fails startup`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.vector" to "simple",
                "app.service.core.embedding" to "mock",
                "app.service.core.embedding.identity" to "acme-e5",
            )
        )

        assertThat(failure.message).contains("app.service.core.embedding.identity")
    }

    @Test
    fun `an identity with an illegal character fails startup`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.vector" to "simple",
                "app.service.core.embedding" to "openai",
                "app.service.core.embedding.identity" to "Acme_E5",
            )
        )

        assertThat(failure.message).contains("app.service.core.embedding.identity")
    }
```

Change the `legal pairs start` test. A production pair needs an identity.

```kotlin
    @Test
    fun `legal pairs start`() {
        // embedded is excluded. This module's test JVM carries no
        // --add-modules jdk.incubator.vector, so the Vector API check
        // rejects it. The positive path is proven in chat-deploy-memory.
        for ((vector, embedding) in LEGAL_PAIRS.filterNot { it.first == "embedded" }) {
            val properties = mutableMapOf(
                "app.service.core.vector" to vector,
                "app.service.core.embedding" to embedding,
            )
            if (embedding != "mock") {
                properties["app.service.core.embedding.identity"] = "acme-e5-small-v2"
            }
            runner(properties).run { context ->
                assertThat(context).describedAs("%s with %s", vector, embedding).hasNotFailed()
            }
        }
    }
```

- [ ] **Step 7: Run the test and confirm it fails**

```bash
mvn -o -pl chat-core -Dtest=VectorSelectorValidationTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL. The four new tests report no startup failure. `legal pairs
start` fails on the six new pairs.

- [ ] **Step 8: Widen the validation**

Modify `chat-core/src/main/kotlin/com/demo/chat/config/VectorSelectorValidation.kt`.

Replace the `legalPairs` set.

```kotlin
    private val legalPairs = setOf(
        "mock" to "mock",
        "simple" to "mock",
        "redis" to "mock",
        "embedded" to "mock",
        "simple" to "openai",
        "redis" to "openai",
        "embedded" to "openai",
        "simple" to "local",
        "redis" to "local",
        "embedded" to "local",
    )
```

Change the `validate` signature and add the identity check.

```kotlin
    fun validate(vector: String?, embedding: String?, identity: String?) {
        val vectorSet = !vector.isNullOrBlank()
        val embeddingSet = !embedding.isNullOrBlank()
        if (!vectorSet && !embeddingSet) return

        if (vectorSet != embeddingSet) {
            throw IllegalStateException(
                "Recall selector pair incomplete: app.service.core.vector=$vector, " +
                    "app.service.core.embedding=$embedding. Both selectors must be set together."
            )
        }

        if (vector to embedding !in legalPairs) {
            throw IllegalStateException(
                "Illegal recall selector pair: app.service.core.vector=$vector, " +
                    "app.service.core.embedding=$embedding. Legal pairs: " +
                    "$legalPairsDescription."
            )
        }

        // Checked after the pair. An illegal pair is the larger error, and the
        // identity rule reads the embedding value that the pair check accepts.
        EmbeddingIdentity.of(embedding!!, identity)

        // Checked last. An illegal pair is a configuration error and must be
        // reported as one, whatever this JVM can load.
        if (vector == EMBEDDED && !vectorApiPresent()) {
            throw IllegalStateException(
                "Recall selector app.service.core.vector=embedded needs the Vector API. " +
                    "The module jdk.incubator.vector is absent from this JVM. " +
                    "Add --add-modules jdk.incubator.vector to the launch command. " +
                    "Without it the vector store fails at the first recall, not at startup."
            )
        }
    }
```

Add the import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

Change the configuration class to read the third property, and move the check
to a BeanFactoryPostProcessor.

A BeanFactoryPostProcessor runs before the container builds any singleton, and
a SmartInitializingSingleton runs after every singleton exists. The later
moment is too late for two reasons. An incomplete pair removes the
EmbeddingIdentity bean, so a provider that injects the identity fails with
NoSuchBeanDefinitionException and hides the real error. An illegal pair loads
an 86.2 MiB ONNX model before anything reports the pair.

```kotlin
/**
 * Runs the selector check before the container builds any singleton.
 *
 * The class reads the Environment and not a bean, because no bean exists at
 * this moment.
 */
open class VectorSelectorValidationPostProcessor(
    private val environment: Environment,
) : BeanFactoryPostProcessor {

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        VectorSelectorValidation.validate(
            environment.getProperty("app.service.core.vector"),
            environment.getProperty("app.service.core.embedding"),
            environment.getProperty(EmbeddingIdentity.PROPERTY),
        )
    }
}

@Configuration
open class VectorSelectorValidationConfiguration {

    companion object {
        /**
         * The method is static, which is what Spring requires of a
         * BeanFactoryPostProcessor bean. A method on the instance would build
         * the configuration class before every bean post processor exists.
         */
        @Bean
        @JvmStatic
        fun vectorSelectorValidation(environment: Environment): BeanFactoryPostProcessor =
            VectorSelectorValidationPostProcessor(environment)
    }
}
```

Replace the SmartInitializingSingleton import with these three imports.

```kotlin
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.core.env.Environment
```

- [ ] **Step 9: Run both test classes and confirm they pass**

```bash
mvn -o -pl chat-core -Dtest=VectorSelectorValidationTests,EmbeddingIdentityTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS. `VectorSelectorValidationTests` reports 13 tests. The class
held 10, this task deletes 1, and this task adds 4.
`EmbeddingIdentityTests` reports 9 tests.

- [ ] **Step 10: Run the whole module**

```bash
mvn -o -pl chat-core clean test
```

Expected: PASS. No other test reads `validate`.

- [ ] **Step 11: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add chat-core/src/main/kotlin/com/demo/chat/domain/EmbeddingIdentity.kt \
        chat-core/src/main/kotlin/com/demo/chat/config/EmbeddingIdentityConfiguration.kt \
        chat-core/src/main/kotlin/com/demo/chat/config/VectorSelectorValidation.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/config/EmbeddingIdentityTests.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/config/VectorSelectorValidationTests.kt
git commit -F - <<'MSG'
feat: add the typed embedding identity and its rules (CHAT-etfnihnu)

Every production model carries an identity that the operator sets. The
code never derives one. A base URL and a model name do not identify the
output of a remote service, because a compatible service can change its
model behind both values and return different vectors for the same text.

A mock embedding resolves the fixed identity mock, and it refuses an
operator value. A production embedding requires one. Any value must match
[a-z0-9][a-z0-9-]{0,63}, because a redis key prefix and a directory name
both carry it.

One bean resolves the value, and it is absent when both vector selectors
are absent. Most deployments set neither selector today, and they must keep
starting.

The legal selector pair set grows from four to ten. No mock vector store
takes a production model, because no mock configuration can receive one.

Evidence, measured on <DATE>.

- <N> tests pass in EmbeddingIdentityTests.
- <N> tests pass in VectorSelectorValidationTests.
- chat-core passes in full.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 2: The OpenAI provider module

**Files:**
- Create: `chat-embedding-openai/pom.xml`
- Create: `chat-embedding-openai/src/main/kotlin/com/demo/chat/config/embedding/openai/OpenAiEmbeddingConfiguration.kt`
- Create: `chat-embedding-openai/src/test/kotlin/com/demo/chat/test/embedding/openai/OpenAiEmbeddingConfigurationTests.kt`
- Modify: `pom.xml`

**Interfaces:**
- Consumes: nothing from Task 1. This module reads no identity.
- Produces: a bean of type `org.springframework.ai.embedding.EmbeddingModel`,
  named `openAiEmbeddingModel`. It is present only when
  `app.service.core.embedding` is `openai`. The bean reads an optional
  `app.service.core.embedding.openai.max-attempts`. Task 8 needs that property.
  See the retry note in that task.

- [ ] **Step 1: Add this module to the reactor**

Modify `pom.xml`. Add one entry after `<module>chat-vector-embedded</module>`.

```xml
        <module>chat-embedding-openai</module>
```

Add only this entry. A reactor entry with no directory fails every build in the
repository. Task 3 adds its own entry when it creates its own directory.

- [ ] **Step 2: Write the module pom**

Create `chat-embedding-openai/pom.xml`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.demo</groupId>
        <artifactId>chat-parent</artifactId>
        <version>0.0.1</version>
    </parent>

    <groupId>com.demo</groupId>
    <artifactId>chat-embedding-openai</artifactId>
    <version>0.0.1</version>
    <name>chat-embedding-openai</name>
    <description>OpenAI-compatible EmbeddingModel provider for app.service.core.embedding=openai</description>

    <dependencies>
        <!-- The model library, and not the starter. A starter carries
             auto-configuration. Both provider modules sit on one classpath,
             and two starters would let Spring AI build models that no
             selector asked for. -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <configuration>
                    <args>
                        <arg>-Xjsr305=strict</arg>
                    </args>
                    <compilerPlugins>
                        <plugin>spring</plugin>
                    </compilerPlugins>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-allopen</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

This module declares no `chat-core`. It supplies an `EmbeddingModel` and reads
no repository type.

- [ ] **Step 3: Resolve the library once, online**

```bash
mvn -pl chat-embedding-openai dependency:resolve
```

Expected: the build downloads `spring-ai-openai` and its transitive artifacts.

This step needs network access. A read on 2026-09-13 found only metadata under
`~/.m2` for this artifact.

- [ ] **Step 4: Read the library surface before you write the configuration**

This plan states the expected constructor shape. The jar was not on this
machine when the plan was written, so the shape is unverified. Read the real
signatures first.

```bash
JAR=$(find ~/.m2/repository/org/springframework/ai/spring-ai-openai -name '*.jar' ! -name '*-sources.jar' | head -1)
unzip -p "$JAR" 'org/springframework/ai/openai/OpenAiEmbeddingModel.class' | javap -c /dev/stdin 2>/dev/null | head -30
javap -classpath "$JAR" org.springframework.ai.openai.OpenAiEmbeddingModel | head -20
javap -classpath "$JAR" org.springframework.ai.openai.api.OpenAiApi | head -30
javap -classpath "$JAR" org.springframework.ai.openai.OpenAiEmbeddingOptions | head -20
```

Write down the real constructor and builder names. Use them in Step 6. If they
differ from the code below, keep the behaviour and change the calls.

- [ ] **Step 5: Write the failing test**

Create `chat-embedding-openai/src/test/kotlin/com/demo/chat/test/embedding/openai/OpenAiEmbeddingConfigurationTests.kt`.

```kotlin
package com.demo.chat.test.embedding.openai

import com.demo.chat.config.embedding.openai.OpenAiEmbeddingConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * The bean builds and it stays behind its selector.
 *
 * No test here calls the remote service. A build proves the wiring, and the
 * packaged gate proves the request and the response.
 */
class OpenAiEmbeddingConfigurationTests {

    @Test
    fun `the selector openai supplies an embedding model`() {
        runner(
            mapOf(
                "app.service.core.embedding" to "openai",
                "app.service.core.embedding.openai.base-url" to "http://localhost:9999",
                "app.service.core.embedding.openai.api-key" to "not-a-secret",
                "app.service.core.embedding.openai.model" to "text-embedding-3-small",
            )
        ).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(EmbeddingModel::class.java)
        }
    }

    @Test
    fun `another selector value supplies no bean`() {
        for (other in listOf("mock", "local")) {
            runner(mapOf("app.service.core.embedding" to other)).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(EmbeddingModel::class.java)
            }
        }
    }

    @Test
    fun `an absent selector supplies no bean`() {
        runner(emptyMap()).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(EmbeddingModel::class.java)
        }
    }

    @Test
    fun `an absent property fails the context and names that property`() {
        val complete = mapOf(
            "app.service.core.embedding.openai.base-url" to "http://localhost:9999",
            "app.service.core.embedding.openai.api-key" to "not-a-secret",
            "app.service.core.embedding.openai.model" to "text-embedding-3-small",
        )

        for (absent in complete.keys) {
            val failure = failureFor(
                complete.filterKeys { it != absent } + ("app.service.core.embedding" to "openai")
            )

            assertThat(failure)
                .describedAs("expected a failure that names %s", absent)
                .hasMessageContaining(absent)
        }
    }

    @Test
    fun `a blank property fails the context and names that property`() {
        // A blank key reaches a remote service as an anonymous call, and an
        // operator cannot tell a missing key from an intended one. The same
        // reasoning holds for a blank base URL and a blank model name. So a
        // blank value is not a value.
        val complete = mapOf(
            "app.service.core.embedding.openai.base-url" to "http://localhost:9999",
            "app.service.core.embedding.openai.api-key" to "not-a-secret",
            "app.service.core.embedding.openai.model" to "text-embedding-3-small",
        )

        for (blanked in complete.keys) {
            for (blank in listOf("", "   ")) {
                val failure = failureFor(
                    complete + mapOf(
                        blanked to blank,
                        "app.service.core.embedding" to "openai",
                    )
                )

                assertThat(failure)
                    .describedAs("expected a failure that names %s, blank '%s'", blanked, blank)
                    .hasMessageContaining(blanked)
            }
        }
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withUserConfiguration(OpenAiEmbeddingConfiguration::class.java)

    private fun failureFor(properties: Map<String, String>): Throwable {
        var failure: Throwable? = null
        runner(properties).run { context -> failure = context.startupFailure }
        return failure ?: error("expected a startup failure for $properties")
    }
}
```

- [ ] **Step 6: Run the test and confirm it fails**

```bash
mvn -o -pl chat-embedding-openai test
```

Expected: a compile failure. `OpenAiEmbeddingConfiguration` does not exist.

- [ ] **Step 7: Write the configuration**

Create
`chat-embedding-openai/src/main/kotlin/com/demo/chat/config/embedding/openai/OpenAiEmbeddingConfiguration.kt`.

Use the real signatures from Step 4 of this task.

```kotlin
package com.demo.chat.config.embedding.openai

import org.springframework.ai.document.MetadataMode
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * An EmbeddingModel that reaches an OpenAI-compatible endpoint.
 *
 * Spring AI reaches such an endpoint through a base URL, so this provider
 * serves OpenAI and any service that speaks the same API.
 *
 * The module depends on the model library and not on the starter. A starter
 * carries auto-configuration. Both provider modules sit on one classpath, and
 * two starters would let Spring AI build models that no selector asked for.
 * The selector must be the only thing that decides. So this class constructs
 * the model, and a condition guards it.
 *
 * All three properties are required. OpenAiApi accepts a NoopApiKey in Spring
 * AI 1.0.3, so the library does not force a key. This design requires one
 * anyway. A blank key reaches a remote service as an anonymous call, and an
 * operator cannot tell a missing key from an intended one.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["embedding"], havingValue = "openai")
class OpenAiEmbeddingConfiguration {

    @Bean
    fun openAiEmbeddingModel(
        @Value("\${app.service.core.embedding.openai.base-url:}") baseUrl: String,
        @Value("\${app.service.core.embedding.openai.api-key:}") apiKey: String,
        @Value("\${app.service.core.embedding.openai.model:}") model: String,
        @Value("\${app.service.core.embedding.openai.max-attempts:}") maxAttempts: String,
    ): EmbeddingModel {
        val api = OpenAiApi.builder()
            .baseUrl(required("app.service.core.embedding.openai.base-url", baseUrl))
            .apiKey(required("app.service.core.embedding.openai.api-key", apiKey))
            .build()

        return OpenAiEmbeddingModel(
            api,
            MetadataMode.EMBED,
            OpenAiEmbeddingOptions.builder()
                .model(required("app.service.core.embedding.openai.model", model))
                .build(),
            retryTemplateFor(maxAttempts),
        )
    }

    /**
     * The retry policy of one embedding call.
     *
     * An unset property gives RetryUtils.DEFAULT_RETRY_TEMPLATE, which is what
     * the library uses. That template makes 10 attempts and waits between
     * them, and it needs near 10 minutes to give up on an endpoint that
     * refuses every connection.
     *
     * A set value gives the same policy with that number of attempts. The
     * value 1 makes one attempt and no retry, which a test needs. Task 8 needs
     * it, because a test of a dead endpoint cannot wait 10 minutes.
     *
     * The built template matches the library template in every other way. It
     * retries the same two exception types, and it waits 2 seconds, then 5
     * times longer each attempt, up to 180 seconds. It carries no log
     * listener, which is the one difference.
     */
    private fun retryTemplateFor(maxAttempts: String): RetryTemplate {
        if (maxAttempts.isBlank()) return RetryUtils.DEFAULT_RETRY_TEMPLATE

        val attempts = maxAttempts.trim().toIntOrNull()
        if (attempts == null || attempts < 1) {
            throw IllegalStateException(
                "app.service.core.embedding.openai.max-attempts=$maxAttempts is not a " +
                    "whole number of 1 or more. Remove the property to use the default " +
                    "policy of 10 attempts."
            )
        }

        return RetryTemplate.builder()
            .maxAttempts(attempts)
            .retryOn(TransientAiException::class.java)
            .retryOn(ResourceAccessException::class.java)
            .exponentialBackoff(
                Duration.ofMillis(2000),
                5.0,
                Duration.ofMillis(180000),
            )
            .build()
    }

    /**
     * Each property carries an empty default, and this function rejects it.
     *
     * A @Value with no default fails the context, but the message names the
     * bean and the parameter rather than the property. An operator reads the
     * property name. A blank value must fail the same way as an absent one,
     * because a blank key reaches a remote service as an anonymous call.
     */
    private fun required(property: String, value: String): String {
        if (value.isBlank()) {
            throw IllegalStateException(
                "$property is not set, and app.service.core.embedding=openai. " +
                    "This provider requires a value that is not blank."
            )
        }
        return value
    }
}
```

- [ ] **Step 8: Run the test and confirm it passes**

```bash
mvn -o -pl chat-embedding-openai test
```

Expected: PASS, 7 tests. Five cover the selector and the three required
properties. Two cover max-attempts.

- [ ] **Step 9: Prove the module carries no Spring AI starter and no Spring AI auto-configuration**

A starter carries auto-configuration. Both provider modules sit on one
classpath, and a starter would let Spring AI build a model that no selector
asked for.

A Kotlin test cannot prove this. `ClassLoader.getResource` returns the first
match on the whole test classpath, and `spring-boot-starter-test` supplies its
own copy of the auto-configuration import file. So the check reads the resolved
dependency tree instead.

```bash
TREE=$(mktemp)
mvn -o -pl chat-embedding-openai dependency:tree -DoutputFile="$TREE" -DoutputType=text \
    || { echo "resolution failed"; exit 1; }
if grep -Eq 'org\.springframework\.ai:spring-ai-(starter|autoconfigure)' "$TREE"; then
    echo "a Spring AI starter or auto-configuration artifact resolves here:"
    grep -E 'org\.springframework\.ai:spring-ai-(starter|autoconfigure)' "$TREE"
    exit 1
fi
echo "ok, no Spring AI starter and no Spring AI auto-configuration"
grep 'org.springframework.ai' "$TREE"
```

Expected: `ok, no Spring AI starter and no Spring AI auto-configuration`. The
last command lists the Spring AI artifacts that do resolve. `spring-ai-openai` must
appear. No artifact whose name begins `spring-ai-starter` or
`spring-ai-autoconfigure` may appear.

Use `grep -Eq` inside an `if`. A bare `grep -c` exits with status 1 when it
counts zero, so the success case would read as a failure.

- [ ] **Step 10: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add pom.xml chat-embedding-openai
git commit -F - <<'MSG'
feat: add the openai embedding provider module (CHAT-etfnihnu)

The module supplies an EmbeddingModel when app.service.core.embedding is
openai. Spring AI reaches an OpenAI compatible endpoint through a base URL,
so this provider serves OpenAI and any service that speaks the same API.

The module depends on spring-ai-openai and not on a starter. A starter
carries auto-configuration, both provider modules sit on one classpath, and
two starters would let Spring AI build models that no selector asked for.
The selector must be the only thing that decides.

All three properties are required and none may be blank. OpenAiApi accepts
a NoopApiKey in Spring AI 1.0.3, so the library does not force a key. This
design requires one anyway, because a blank key reaches a remote service as
an anonymous call and an operator cannot tell a missing key from an
intended one.

Evidence, measured on <DATE>.

- <N> tests pass in OpenAiEmbeddingConfigurationTests.
- The dependency tree carries spring-ai-openai and no artifact named
  spring-ai-starter or spring-ai-autoconfigure.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 3: The local provider module

**Files:**
- Create: `chat-embedding-local/pom.xml`
- Create: `chat-embedding-local/src/main/kotlin/com/demo/chat/config/embedding/local/LocalEmbeddingConfiguration.kt`
- Create: `chat-embedding-local/src/test/kotlin/com/demo/chat/test/embedding/local/LocalEmbeddingConfigurationTests.kt`
- Create: `chat-embedding-local/src/test/kotlin/com/demo/chat/test/embedding/local/LocalEmbeddingModelTests.kt`
- Create: `chat-embedding-local/src/test/kotlin/com/demo/chat/test/embedding/local/ComposedLocalSelectorTests.kt`
- Modify: `pom.xml`

**Interfaces:**
- Consumes: `com.demo.chat.domain.EmbeddingIdentity` from Task 1.
- Produces: a bean of type `org.springframework.ai.embedding.EmbeddingModel`,
  named `localEmbeddingModel`. It is present only when
  `app.service.core.embedding` is `local`.

This module is the fifth identity call site. It builds a cache directory that
carries the identity.

- [ ] **Step 1: Add this module to the reactor**

Modify `pom.xml`. Add one entry after `<module>chat-embedding-openai</module>`.

```xml
        <module>chat-embedding-local</module>
```

- [ ] **Step 2: Write the module pom**

Create `chat-embedding-local/pom.xml`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.demo</groupId>
        <artifactId>chat-parent</artifactId>
        <version>0.0.1</version>
    </parent>

    <groupId>com.demo</groupId>
    <artifactId>chat-embedding-local</artifactId>
    <version>0.0.1</version>
    <name>chat-embedding-local</name>
    <description>In-process ONNX EmbeddingModel provider for app.service.core.embedding=local</description>

    <dependencies>
        <!-- chat-core supplies EmbeddingIdentity. This module is the fifth
             identity call site. -->
        <dependency>
            <groupId>com.demo</groupId>
            <artifactId>chat-core</artifactId>
            <version>0.0.1</version>
        </dependency>
        <!-- The model library, and not the starter. See the openai module. -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-transformers</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <configuration>
                    <args>
                        <arg>-Xjsr305=strict</arg>
                    </args>
                    <compilerPlugins>
                        <plugin>spring</plugin>
                    </compilerPlugins>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-allopen</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Resolve the library once, online**

```bash
mvn -pl chat-embedding-local dependency:resolve
```

Expected: the build downloads `spring-ai-transformers`, the ONNX runtime, and
the tokenizer library.

- [ ] **Step 4: Read the library surface before you write the configuration**

```bash
JAR=$(find ~/.m2/repository/org/springframework/ai/spring-ai-transformers -name '*.jar' ! -name '*-sources.jar' | head -1)
javap -classpath "$JAR" org.springframework.ai.transformers.TransformersEmbeddingModel | head -40
```

Write down the real setter names. This plan expects `setModelResource`,
`setTokenizerResource`, `setResourceCacheDirectory`, and `afterPropertiesSet`.
The ONNX documentation for Spring AI 2.0 describes a different surface. Follow
the 1.0 surface that this command prints.

- [ ] **Step 5: Write the failing test**

Create
`chat-embedding-local/src/test/kotlin/com/demo/chat/test/embedding/local/LocalEmbeddingConfigurationTests.kt`.

```kotlin
package com.demo.chat.test.embedding.local

import com.demo.chat.config.embedding.local.LocalEmbeddingConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Path

/**
 * The selector rule and the cache path rule.
 *
 * No test here loads an ONNX model. A real model is a large file that this
 * repository does not carry, so the model load is a manual procedure. See
 * docs/EMBEDDING-PROVIDERS.md.
 */
class LocalEmbeddingConfigurationTests {

    @Test
    fun `another selector value supplies no bean`() {
        for (other in listOf("mock", "openai")) {
            runner(mapOf("app.service.core.embedding" to other)).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(EmbeddingModel::class.java)
            }
        }
    }

    @Test
    fun `an absent selector supplies no bean`() {
        runner(emptyMap()).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(EmbeddingModel::class.java)
        }
    }

    @Test
    fun `the cache directory carries the identity under the configured base`() {
        val base = "/tmp/chat-embedding-local-test"

        assertThat(
            LocalEmbeddingConfiguration.cacheDirectoryFor(base, EmbeddingIdentity("acme-e5-small-v2"))
        ).isEqualTo(Path.of(base, "acme-e5-small-v2"))
    }

    @Test
    fun `two identities give two cache directories`() {
        val base = "/tmp/chat-embedding-local-test"

        val first = LocalEmbeddingConfiguration.cacheDirectoryFor(base, EmbeddingIdentity("one"))
        val second = LocalEmbeddingConfiguration.cacheDirectoryFor(base, EmbeddingIdentity("two"))

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `a missing model uri fails the context and names the property`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.embedding" to "local",
                "app.service.core.embedding.local.tokenizer-uri" to "file:/tmp/tokenizer.json",
            )
        )

        assertThat(failure).hasMessageContaining("app.service.core.embedding.local.model-uri")
    }

    @Test
    fun `a missing tokenizer uri fails the context and names the property`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.embedding" to "local",
                "app.service.core.embedding.local.model-uri" to "file:/tmp/model.onnx",
            )
        )

        assertThat(failure).hasMessageContaining("app.service.core.embedding.local.tokenizer-uri")
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withBean(EmbeddingIdentity::class.java, { EmbeddingIdentity("acme-e5-small-v2") })
            .withUserConfiguration(LocalEmbeddingConfiguration::class.java)

    private fun failureFor(properties: Map<String, String>): Throwable {
        var failure: Throwable? = null
        runner(properties).run { context -> failure = context.startupFailure }
        return failure ?: error("expected a startup failure for $properties")
    }
}
```

- [ ] **Step 6: Run the test and confirm it fails**

```bash
mvn -o -pl chat-core,chat-embedding-local -Dtest=LocalEmbeddingConfigurationTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: a compile failure. `LocalEmbeddingConfiguration` does not exist.

- [ ] **Step 7: Write the configuration**

Create
`chat-embedding-local/src/main/kotlin/com/demo/chat/config/embedding/local/LocalEmbeddingConfiguration.kt`.

Use the real setter names from Step 3.

```kotlin
package com.demo.chat.config.embedding.local

import com.demo.chat.domain.EmbeddingIdentity
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.transformers.TransformersEmbeddingModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

/**
 * An in-process EmbeddingModel that loads an ONNX model and a tokenizer.
 *
 * Spring AI reads file:, classpath:, and https: resources. So an operator
 * supplies a model from disk, from the artifact, or from a remote host.
 *
 * The module depends on the model library and not on the starter. See
 * OpenAiEmbeddingConfiguration for the reason.
 *
 * The cache directory carries the identity, and that is the point of this
 * class. ResourceCacheService in Spring AI 1.0.3 caches a remote resource by
 * its location. A new identity with unchanged URIs would load the old bytes,
 * and the corpus would carry vectors from the previous model under the new
 * name.
 *
 * An operator who wants no caching names the local resources with file:, which
 * the cache does not copy.
 *
 * TransformersEmbeddingModel implements InitializingBean, so the container
 * loads the model after this factory method returns. The method must not call
 * afterPropertiesSet itself. A direct call loads the 86.2 MiB ONNX file twice
 * and builds two ONNX sessions, and the second session replaces the first.
 * The model still loads during startup, so an absent file still fails startup.
 *
 * Both URI properties are required, and the module reads each one with an
 * empty default. See the required function below for the reason.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["embedding"], havingValue = "local")
class LocalEmbeddingConfiguration {

    @Bean
    fun localEmbeddingModel(
        identity: EmbeddingIdentity,
        @Value("\${app.service.core.embedding.local.model-uri:}") modelUri: String,
        @Value("\${app.service.core.embedding.local.tokenizer-uri:}") tokenizerUri: String,
        @Value("\${app.service.core.embedding.local.cache-path:#{systemProperties['java.io.tmpdir']}/chat-embedding-local}")
        cachePath: String,
    ): EmbeddingModel {
        val cacheDirectory = cacheDirectoryFor(cachePath, identity)
        Files.createDirectories(cacheDirectory)

        val model = TransformersEmbeddingModel()
        model.setModelResource(required("app.service.core.embedding.local.model-uri", modelUri))
        model.setTokenizerResource(
            required("app.service.core.embedding.local.tokenizer-uri", tokenizerUri)
        )
        model.setResourceCacheDirectory(cacheDirectory.toString())
        return model
    }

    /**
     * Each URI property carries an empty default, and this function rejects it.
     *
     * A @Value with no default fails the context, and the property name then
     * sits in the cause of the failure rather than in its message. An operator
     * reads the property name. The openai provider uses the same function for
     * the same reason.
     */
    private fun required(property: String, value: String): String {
        if (value.isBlank()) {
            throw IllegalStateException(
                "$property is not set, and app.service.core.embedding=local. " +
                    "This provider requires a value that is not blank."
            )
        }
        return value
    }

    companion object {
        /**
         * The cache directory of one identity, under the configured base.
         *
         * The default base is ephemeral, which follows the embedded store. The
         * corpus is a derived cache, so a lost cache is a rebuild.
         */
        fun cacheDirectoryFor(base: String, identity: EmbeddingIdentity): Path =
            Path.of(base, identity.value)
    }
}
```

- [ ] **Step 8: Run the test and confirm it passes**

```bash
mvn -o -pl chat-core,chat-embedding-local -Dtest=LocalEmbeddingConfigurationTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, 6 tests.

An earlier shape of this configuration read each URI property with no default.
Two tests then failed. Measured on 2026-09-14: an ApplicationContextRunner
registers no PropertySourcesPlaceholderConfigurer, so an absent property
reaches the bean as the placeholder text. A runner that registers that bean
fails, and the message reads "Unexpected exception during bean creation". The
property name sits in the cause. So this module rejects a blank value the way
the openai module does.

- [ ] **Step 8a: Write the composed context tests**

Create
`chat-embedding-local/src/test/kotlin/com/demo/chat/test/embedding/local/ComposedLocalSelectorTests.kt`.

`LocalEmbeddingConfigurationTests` supplies the identity as a test bean, so it
cannot show what happens when the resolver refuses to supply one. This class
composes `VectorSelectorValidationConfiguration`,
`EmbeddingIdentityConfiguration`, and `LocalEmbeddingConfiguration`, which is
the shape a deployment builds.

Three tests, one for each moment.

1. `app.service.core.embedding=local` with no vector reports both selectors.
2. `vector=mock` with `embedding=local` reports the illegal pair.
3. A legal pair with no identity reports the identity property.

Every model URI names a file that does not exist. A message that names that
file proves that the container built the model, so each test also asserts that
the message does not name it.

```bash
mvn -o -pl chat-core,chat-embedding-local -Dtest=ComposedLocalSelectorTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, 3 tests.

Prove that these three tests hold the rule. Make
`VectorSelectorValidationPostProcessor.postProcessBeanFactory` return before it
calls `validate`, run the class again, and restore the line. Measured on
2026-09-14: two of the three failed, and the first reported
`No qualifying bean of type 'com.demo.chat.domain.EmbeddingIdentity'`.

- [ ] **Step 9: Prove the module carries no Spring AI starter and no Spring AI auto-configuration**

A starter carries auto-configuration. Both provider modules sit on one
classpath, and a starter would let Spring AI build a model that no selector
asked for.

A Kotlin test cannot prove this. `ClassLoader.getResource` returns the first
match on the whole test classpath, and `spring-boot-starter-test` supplies its
own copy of the auto-configuration import file. So the check reads the resolved
dependency tree instead.

```bash
TREE=$(mktemp)
mvn -o -pl chat-embedding-local dependency:tree -DoutputFile="$TREE" -DoutputType=text \
    || { echo "resolution failed"; exit 1; }
if grep -Eq 'org\.springframework\.ai:spring-ai-(starter|autoconfigure)' "$TREE"; then
    echo "a Spring AI starter or auto-configuration artifact resolves here:"
    grep -E 'org\.springframework\.ai:spring-ai-(starter|autoconfigure)' "$TREE"
    exit 1
fi
echo "ok, no Spring AI starter and no Spring AI auto-configuration"
grep 'org.springframework.ai' "$TREE"
```

Expected: `ok, no Spring AI starter and no Spring AI auto-configuration`. The
last command lists the Spring AI artifacts that do resolve. `spring-ai-transformers` must
appear. No artifact whose name begins `spring-ai-starter` or
`spring-ai-autoconfigure` may appear.

Use `grep -Eq` inside an `if`. A bare `grep -c` exits with status 1 when it
counts zero, so the success case would read as a failure.

- [ ] **Step 10: Download the pinned model files**

Every test so far proves that the bean stays behind its selector, and that the
cache path carries the identity. None of them loads a model. So none of them
proves that this module can supply a working `EmbeddingModel`.

The model is `all-MiniLM-L6-v2`, which Spring AI documents for this provider.
It emits 384 dimensions.

**The revision is pinned and both checksums are measured.** A `main` URL serves
whatever that branch holds today. The identity states which model wrote a
corpus, so a mutable URL under a fixed identity would let two different models
share one name. That is the exact failure the identity exists to prevent.

The values below were measured on 2026-09-14 against the pinned revision.

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

# The checksum file names each file without a directory, so the check runs
# inside the cache directory. The subshell keeps that change of directory out
# of the caller's shell.
( cd "$CACHE" && shasum -a 256 -c - <<'SUMS'
6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452  model.onnx
be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037  tokenizer.json
SUMS
)
```

Expected: two lines that each end `OK`.

**Stop on any mismatch.** A mismatch means the pinned revision served other
bytes. Report it to the owner. Do not download from `main`, and do not change a
checksum to match what arrived.

This download needs network access. It runs once. The files stay outside the
repository, and no build commits them.

**The whole task downloads five file sets, which is 433.3 MiB.** One file set
is 90871461 bytes, which is 86.7 MiB. Five sets are 454357305 bytes.

| Run | Sets | MiB |
|---|---|---|
| This step, the two `curl` commands | 1 | 86.7 |
| Step 12, the first mutation, remote property on | 2 | 173.3 |
| Step 12, the second mutation, remote property off | 0 | 0 |
| Step 13, the green run, remote property on | 2 | 173.3 |
| Step 13, the skip check, neither property | 0 | 0 |
| **Total** | **5** | **433.3** |

The remote test takes two sets, because it builds two identities and a new
identity must not read the cache of the old one.

Run the steps in order, and do not run the remote test more often than the plan
says. Each extra run with the remote property costs another 173.3 MiB.

- [ ] **Step 11: Write the real model tests**

Create
`chat-embedding-local/src/test/kotlin/com/demo/chat/test/embedding/local/LocalEmbeddingModelTests.kt`.

```kotlin
package com.demo.chat.test.embedding.local

import com.demo.chat.config.embedding.local.LocalEmbeddingConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a real model through the configuration, and embeds real text.
 *
 * The other tests in this module prove the selector rule and the cache path
 * function. None of them loads a model, so none of them proves that this
 * module can supply a working EmbeddingModel.
 *
 * The revision is pinned. A main URL serves whatever that branch holds today,
 * and the identity states which model wrote a corpus. A mutable URL under a
 * fixed identity would let two different models share one name.
 *
 * Every test here needs an opt in, which is what the design document
 * requires. It says that a run of this module stays manual and never runs
 * unattended.
 *
 * The integration tag keeps all three out of the default build.
 * -Dchat.embedding.local.manual=true is the switch for the class. Without it
 * every test here is skipped, whatever sits in the cache directory. A check on
 * the downloaded files alone would not hold the rule, because a developer who
 * ran the download once would load a 90 MiB model in every later integration
 * build.
 *
 * The two file tests also need the two downloaded files, which live outside
 * the repository because they total near 87 MiB. The one remote test also
 * needs -Dchat.embedding.local.remote=true, because it downloads two more
 * sets of that size. The plan step beside this class carries the download,
 * the two checksums, and both properties.
 */
@Tag("integration")
class LocalEmbeddingModelTests {

    companion object {
        const val REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41"
        const val BASE =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/$REVISION"
        const val EXPECTED_DIMENSIONS = 384

        val modelPath: Path = Path.of(
            System.getProperty("user.home"), ".cache", "chat-embedding-local-model", "model.onnx"
        )
        val tokenizerPath: Path = Path.of(
            System.getProperty("user.home"), ".cache", "chat-embedding-local-model", "tokenizer.json"
        )

        /**
         * The opt in that every test in this class needs.
         *
         * The design document states that a run of this module stays manual
         * and never runs unattended. A check on the downloaded files alone
         * would not hold that rule. Once a developer runs the download, every
         * later integration build on that machine would load a 90 MiB model
         * without anyone asking for it.
         */
        @JvmStatic
        fun manualEnabled(): Boolean =
            System.getProperty("chat.embedding.local.manual") == "true"

        /** The opt in, and the two downloaded files. */
        @JvmStatic
        fun localModelEnabled(): Boolean =
            manualEnabled() &&
                Files.isRegularFile(modelPath) &&
                Files.isRegularFile(tokenizerPath)

        /**
         * The opt in, and a second one for the network.
         *
         * The one remote test downloads two file sets, which is near 173 MiB.
         * So it carries its own switch beside the class switch.
         */
        @JvmStatic
        fun remoteEnabled(): Boolean =
            manualEnabled() &&
                System.getProperty("chat.embedding.local.remote") == "true"
    }

    private fun runnerFor(
        modelUri: String,
        tokenizerUri: String,
        cache: Path,
        identity: String,
    ): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(
                "app.service.core.embedding=local",
                "app.service.core.embedding.local.model-uri=$modelUri",
                "app.service.core.embedding.local.tokenizer-uri=$tokenizerUri",
                "app.service.core.embedding.local.cache-path=$cache",
            )
            .withBean(EmbeddingIdentity::class.java, { EmbeddingIdentity(identity) })
            .withUserConfiguration(LocalEmbeddingConfiguration::class.java)

    @Test
    @EnabledIf("localModelEnabled")
    fun `the bean loads a real model and embeds text`() {
        val cache = Files.createTempDirectory("local-model-test")

        runnerFor("file:$modelPath", "file:$tokenizerPath", cache, "minilm-l6-v2")
            .run { context ->
                assertThat(context).hasNotFailed()

                val model = context.getBean(EmbeddingModel::class.java)

                assertThat(model.dimensions())
                    .describedAs("all-MiniLM-L6-v2 emits %d dimensions", EXPECTED_DIMENSIONS)
                    .isEqualTo(EXPECTED_DIMENSIONS)

                val vector = model.embed("apple pie recipe")
                assertThat(vector).hasSize(EXPECTED_DIMENSIONS)
                assertThat(vector.any { it != 0f })
                    .describedAs("a real model returns a vector that is not all zero")
                    .isTrue()
            }
    }

    @Test
    @EnabledIf("localModelEnabled")
    fun `two texts that share meaning score above two that do not`() {
        val cache = Files.createTempDirectory("local-model-test")

        runnerFor("file:$modelPath", "file:$tokenizerPath", cache, "minilm-l6-v2")
            .run { context ->
                val model = context.getBean(EmbeddingModel::class.java)

                val near = cosine(
                    model.embed("a recipe for apple pie"),
                    model.embed("how to bake an apple tart"),
                )
                val far = cosine(
                    model.embed("a recipe for apple pie"),
                    model.embed("the compiler emits bytecode"),
                )

                // This is the whole point of a real model. The mock matches on
                // shared substrings, and this one matches on meaning.
                assertThat(near)
                    .describedAs("near %s must beat far %s", near, far)
                    .isGreaterThan(far)
            }
    }

    @Test
    @EnabledIf("remoteEnabled")
    fun `a remote model caches under a directory per identity`() {
        // This test uses https, and that choice is the point of it.
        // ResourceCacheService copies a remote resource into the cache
        // directory. It does not copy a file: resource, so a file: test can
        // never show that the bean passed the right cache directory. A
        // hardcoded setter would pass every other test in this class.
        //
        // Two identities in one test, and not two tests. An earlier shape of
        // this class held one test of a single identity beside one test of
        // two, which cost three file sets per run. This shape costs two, and
        // it proves the same two facts.
        //
        // The second identity is the point. A new identity with unchanged
        // URIs must not load the old bytes, which is the defect this cache
        // directory exists to prevent.
        //
        // This test needs network access, so it needs its own opt in beside
        // the class opt in. The design document keeps every run of this module
        // manual.
        val cache = Files.createTempDirectory("local-model-cache-test")
        val identities = listOf("cache-one", "cache-two")

        for (identity in identities) {
            runnerFor("$BASE/onnx/model.onnx", "$BASE/tokenizer.json", cache, identity)
                .run { context ->
                    assertThat(context).describedAs(identity).hasNotFailed()
                }
        }

        for (identity in identities) {
            val identityDirectory = cache.resolve(identity)

            assertThat(Files.isDirectory(identityDirectory))
                .describedAs("the bean must cache under the directory of %s", identity)
                .isTrue()

            val cached = Files.walk(identityDirectory).use { walk ->
                walk.filter { Files.isRegularFile(it) }.toList()
            }

            assertThat(cached)
                .describedAs("the cache directory of %s must hold the copies", identity)
                .isNotEmpty()

            assertThat(cached.sumOf { Files.size(it) })
                .describedAs("a cached ONNX model is tens of megabytes")
                .isGreaterThan(1_000_000L)
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb))
    }
}
```

- [ ] **Step 12: Run the tests red, against a broken production path**

The configuration already exists, so these tests cannot fail for the usual
reason. Break the production path instead, and watch each test catch it. A test
that passes under a broken path proves nothing.

Change `LocalEmbeddingConfiguration.localEmbeddingModel`. Replace

```kotlin
        val cacheDirectory = cacheDirectoryFor(cachePath, identity)
```

with a hardcoded directory that ignores both arguments.

```kotlin
        val cacheDirectory = Path.of(cachePath, "shared")
```

```bash
mvn -o -pl chat-core,chat-embedding-local -Pintegration \
    -DargLine="-Dchat.embedding.local.manual=true -Dchat.embedding.local.remote=true" \
    -Dtest=LocalEmbeddingModelTests -Dsurefire.failIfNoSpecifiedTests=false clean verify
```

Both properties must reach the surefire JVM, which is a separate process. So
they travel in `argLine` rather than as plain `-D` arguments to Maven. Confirm
that all three tests report as run rather than skipped.

`chat-embedding-local` declares no `argLine` of its own, so this value sets it
for the run. Read the module pom first and append to the existing value when
one appears there.

Expected: FAIL, in `a remote model caches under a directory per identity`. The
two `file:` tests still pass, which is why this class carries a remote one.

`LocalEmbeddingConfigurationTests` also still passes, because it calls
`cacheDirectoryFor` rather than the bean. That is the gap this step closes.

This run downloads two file sets, which is 173.3 MiB.

Now break the model load. Restore the cache line, then replace

```kotlin
            required("app.service.core.embedding.local.tokenizer-uri", tokenizerUri)
```

with a call that passes the model URI to the tokenizer.

```kotlin
            required("app.service.core.embedding.local.tokenizer-uri", modelUri)
```

Run with the class property only. This mutation needs no remote test, and
leaving the remote property off saves two file sets of download.

```bash
mvn -o -pl chat-core,chat-embedding-local -Pintegration \
    -DargLine="-Dchat.embedding.local.manual=true" \
    -Dtest=LocalEmbeddingModelTests -Dsurefire.failIfNoSpecifiedTests=false clean verify
```

Expected: FAIL, in `the bean loads a real model and embeds text` and in
`two texts that share meaning score above two that do not`. The remote test
reports as skipped.

Restore both lines.

- [ ] **Step 13: Run the real model tests green**

```bash
mvn -o -pl chat-core,chat-embedding-local -Pintegration \
    -DargLine="-Dchat.embedding.local.manual=true -Dchat.embedding.local.remote=true" \
    -Dtest=LocalEmbeddingModelTests -Dsurefire.failIfNoSpecifiedTests=false clean verify
```

Expected: PASS, 3 tests, and none skipped. This run downloads two file sets,
which is 173.3 MiB. A skipped test among the first two
means the two files are absent. Run Step 10 and repeat. A skipped remote test
means a property did not reach the surefire JVM.

Then run the module once more with neither property, and with the downloaded
files still in place.

```bash
mvn -o -pl chat-core,chat-embedding-local -Pintegration clean verify
```

Expected: all three tests report as skipped. That is the check that matters. The
files are on this machine now, so a gate on their presence alone would run a
90 MiB model load in every later integration build. The design document
requires that a run of this module never runs unattended.

A `dimensions()` answer other than 384 means the pinned revision served another
model. Step 10 catches that first, through the checksums. Do not change the
expected value to match the answer.


- [ ] **Step 14: Build the whole reactor**

```bash
mvn -o -B clean test
```

Expected: BUILD SUCCESS. The reactor now reports 37 modules.

- [ ] **Step 15: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add pom.xml chat-embedding-local
git commit -F - <<'MSG'
feat: add the local embedding provider module (CHAT-etfnihnu)

The module supplies an EmbeddingModel when app.service.core.embedding is
local. It depends on spring-ai-transformers and not on a starter, because a
starter carries auto-configuration and both providers sit on one classpath.

The cache directory carries the identity. ResourceCacheService caches a
remote resource by its location, so a new identity with unchanged URIs
would load the old bytes.

Evidence, measured on <DATE>.

- <N> tests pass in LocalEmbeddingConfigurationTests.
- 3 tests pass in LocalEmbeddingModelTests, under -Pintegration with both
  opt in properties. None is skipped.
- With the downloaded files in place and neither property set, all three
  report as skipped. So no unattended build loads the model.
- The task downloaded five file sets, which is 433.3 MiB.
- The dependency tree carries spring-ai-transformers and no artifact named
  spring-ai-starter or spring-ai-autoconfigure.
- The model is all-MiniLM-L6-v2 at revision
  1110a243fdf4706b3f48f1d95db1a4f5529b4d41. Both checksums matched.
- Mutation one: a hardcoded cache directory failed the remote test and
  passed the two file tests.
- Mutation two: a tokenizer resource that took the model URI failed the two
  file tests.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 4: The identity reaches both vector stores

**Files:**
- Modify: `chat-vector-redis/src/main/kotlin/com/demo/chat/config/vector/redis/RedisVectorStoreConfiguration.kt`
- Modify: `chat-vector-embedded/pom.xml`
- Modify: `chat-vector-embedded/src/main/kotlin/com/demo/chat/config/vector/embedded/EmbeddedVectorStoreConfiguration.kt`
- Modify: `chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorStoreConfigurationTests.kt`
- Create: `chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorNamesTests.kt`
- Create: `chat-vector-embedded/src/test/kotlin/com/demo/chat/test/vector/embedded/EmbeddedStorageDirectoryTests.kt`

**Interfaces:**
- Consumes: `com.demo.chat.domain.EmbeddingIdentity` from Task 1.
- Produces:
  - `RedisVectorStoreConfiguration.indexNameFor(keyType: String, identity: EmbeddingIdentity): String`
  - `RedisVectorStoreConfiguration.prefixFor(keyType: String, identity: EmbeddingIdentity): String`
  - `EmbeddedVectorStoreConfiguration.storageDirectoryFor(configuredPath: String, identity: EmbeddingIdentity): Path`

`chat-vector-embedded` declares `chat-core` only as a test jar today. This task
adds the compile scope dependency, because the configuration now reads a
`chat-core` type. The spec does not name that step, and the module cannot
compile without it.

- [ ] **Step 1: Write the failing redis test**

Create
`chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorNamesTests.kt`.

```kotlin
package com.demo.chat.test.vector.redis

import com.demo.chat.config.vector.redis.RedisVectorStoreConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The index name and the key prefix carry the key type and the identity.
 *
 * A metadata field does not isolate a redis index, which is why the key type
 * is already in the name. The identity joins it for the same reason.
 */
class RedisVectorNamesTests {

    @Test
    fun `the index name carries the key type and the identity`() {
        assertThat(
            RedisVectorStoreConfiguration.indexNameFor("long", EmbeddingIdentity("acme-e5"))
        ).isEqualTo("chat:vector:long:acme-e5:message")
    }

    @Test
    fun `the prefix carries the key type and the identity`() {
        assertThat(
            RedisVectorStoreConfiguration.prefixFor("long", EmbeddingIdentity("acme-e5"))
        ).isEqualTo("chat:vector:long:acme-e5:message:")
    }

    @Test
    fun `the prefix is the index name with one separator`() {
        val index = RedisVectorStoreConfiguration.indexNameFor("uuid", EmbeddingIdentity("m2"))
        val prefix = RedisVectorStoreConfiguration.prefixFor("uuid", EmbeddingIdentity("m2"))

        assertThat(prefix).isEqualTo("$index:")
    }

    @Test
    fun `two identities give two index names`() {
        assertThat(RedisVectorStoreConfiguration.indexNameFor("long", EmbeddingIdentity("one")))
            .isNotEqualTo(RedisVectorStoreConfiguration.indexNameFor("long", EmbeddingIdentity("two")))
    }

    @Test
    fun `two key types give two index names`() {
        assertThat(RedisVectorStoreConfiguration.indexNameFor("long", EmbeddingIdentity("one")))
            .isNotEqualTo(RedisVectorStoreConfiguration.indexNameFor("uuid", EmbeddingIdentity("one")))
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
mvn -o -pl chat-core,chat-vector-redis -Dtest=RedisVectorNamesTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: a compile failure. `indexNameFor` does not exist.

- [ ] **Step 3: Change the redis configuration**

Modify
`chat-vector-redis/src/main/kotlin/com/demo/chat/config/vector/redis/RedisVectorStoreConfiguration.kt`.

Add the import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

Change the KDoc line that names the index shape.

```kotlin
/**
 * Shared runtime vector store. Jedis-backed, Redis Stack required.
 *
 * Isolation is per key type and per embedding identity. The index is
 * chat:vector:<keyType>:<identity>:message and the key prefix is
 * chat:vector:<keyType>:<identity>:message:. A metadata field alone does not
 * isolate Redis indexes, so the index name carries both values, and the recall
 * filters carry keyType as well.
 *
 * An index that an earlier build wrote orphans. Its name has no identity
 * segment, and no read reaches it again. The corpus is a derived cache, so a
 * rebuild replaces it.
 */
```

Change the bean and add the two functions.

```kotlin
    @Bean
    fun redisVectorStore(
        embeddingModel: EmbeddingModel,
        environment: Environment,
        identity: EmbeddingIdentity,
        @Value("\${app.key.type}") keyType: String,
    ): VectorStore {
        val host = environment.getProperty("spring.redis.host", "localhost")
        val port = environment.getProperty("spring.redis.port", "6379").toInt()
        val jedis = JedisPooled(host, port)

        return RedisVectorStore.builder(jedis, embeddingModel)
            .indexName(indexNameFor(keyType, identity))
            .prefix(prefixFor(keyType, identity))
            .metadataFields(
                MetadataField.tag("kind"),
                MetadataField.tag("keyType"),
                MetadataField.tag("topicId"),
                MetadataField.tag("userId"),
            )
            .initializeSchema(true)
            .build()
    }

    companion object {
        fun indexNameFor(keyType: String, identity: EmbeddingIdentity): String =
            "chat:vector:$keyType:${identity.value}:message"

        fun prefixFor(keyType: String, identity: EmbeddingIdentity): String =
            "${indexNameFor(keyType, identity)}:"
    }
```

- [ ] **Step 4: Run the redis test and confirm it passes**

```bash
mvn -o -pl chat-core,chat-vector-redis -Dtest=RedisVectorNamesTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, 5 tests.

- [ ] **Step 5: Add the chat-core dependency to the embedded module**

Modify `chat-vector-embedded/pom.xml`. Add a test source directory as well. The
module held no test source before this task, so its pom names only a source
directory, and the Kotlin plugin compiles no test without this line.

```xml
        <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
```

Then add this dependency before the existing `chat-core` test jar entry.

```xml
        <!-- chat-core supplies EmbeddingIdentity. The storage directory
             carries that value. -->
        <dependency>
            <groupId>com.demo</groupId>
            <artifactId>chat-core</artifactId>
            <version>0.0.1</version>
        </dependency>
```

The module declared no compile scope `chat-core` before this change. It
declared `kotlin-stdlib` itself for that reason. Keep that declaration. A
direct dependency stays correct when a transitive path also supplies it.

- [ ] **Step 6: Write the failing embedded test**

Create
`chat-vector-embedded/src/test/kotlin/com/demo/chat/test/vector/embedded/EmbeddedStorageDirectoryTests.kt`.

```kotlin
package com.demo.chat.test.vector.embedded

import com.demo.chat.config.vector.embedded.EmbeddedVectorStoreConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The collection directory carries the identity.
 *
 * The collection takes its width from embeddingModel.dimensions(), and two
 * models rarely share a width. A separate directory per identity means a model
 * change cannot meet a collection of the wrong width.
 */
class EmbeddedStorageDirectoryTests {

    @Test
    fun `a configured path gains the identity segment`() {
        val base = Files.createTempDirectory("embedded-storage-test")

        assertThat(
            EmbeddedVectorStoreConfiguration.storageDirectoryFor(
                base.toString(),
                EmbeddingIdentity("acme-e5"),
            )
        ).isEqualTo(base.resolve("acme-e5"))
    }

    @Test
    fun `a configured path is created when it is absent`() {
        val base = Files.createTempDirectory("embedded-storage-test")
        val absent = base.resolve("deeper")

        val directory = EmbeddedVectorStoreConfiguration.storageDirectoryFor(
            absent.toString(),
            EmbeddingIdentity("acme-e5"),
        )

        assertThat(Files.isDirectory(directory)).isTrue()
    }

    @Test
    fun `an unset path gives a temporary directory that carries the identity`() {
        val directory = EmbeddedVectorStoreConfiguration.storageDirectoryFor(
            "",
            EmbeddingIdentity("acme-e5"),
        )

        assertThat(directory.fileName).isEqualTo(Path.of("acme-e5"))
        assertThat(Files.isDirectory(directory)).isTrue()
    }

    @Test
    fun `two identities give two directories`() {
        val base = Files.createTempDirectory("embedded-storage-test").toString()

        val first = EmbeddedVectorStoreConfiguration.storageDirectoryFor(base, EmbeddingIdentity("one"))
        val second = EmbeddedVectorStoreConfiguration.storageDirectoryFor(base, EmbeddingIdentity("two"))

        assertThat(first).isNotEqualTo(second)
    }
}
```

- [ ] **Step 7: Run the test and confirm it fails**

```bash
mvn -o -pl chat-core,chat-vector-embedded -Dtest=EmbeddedStorageDirectoryTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: a compile failure. `storageDirectoryFor` does not exist.

- [ ] **Step 8: Change the embedded configuration**

Modify
`chat-vector-embedded/src/main/kotlin/com/demo/chat/config/vector/embedded/EmbeddedVectorStoreConfiguration.kt`.

Add the import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

Change the collection bean.

```kotlin
    @Bean(destroyMethod = "close")
    fun embeddedVectorCollection(
        embeddingModel: EmbeddingModel,
        identity: EmbeddingIdentity,
        @Value("\${app.service.core.vector.embedded.path:}") configuredPath: String,
    ): VectorCollection =
        VectorCollection.builder()
            .dimension(embeddingModel.dimensions())
            .metric(SimilarityFunction.COSINE)
            .indexType(IndexType.FLAT)
            .storagePath(storageDirectoryFor(configuredPath, identity))
            .build()
```

Delete the private `storageDirectory` function. Put this in the companion
object.

```kotlin
    companion object {
        const val COLLECTION_NAME = "messages"
        const val TEMP_PREFIX = "chat-vector-embedded"

        /**
         * The collection directory of one identity.
         *
         * An unset path gives a temporary directory. That matches the rebuild
         * on failure decision. A set path is created when it does not exist.
         *
         * The identity is the last segment. The collection takes its width
         * from the model, and two models rarely share a width. A separate
         * directory per identity means a model change cannot meet a collection
         * of the wrong width.
         *
         * A directory that an earlier build wrote orphans. Its path has no
         * identity segment.
         */
        fun storageDirectoryFor(configuredPath: String, identity: EmbeddingIdentity): Path {
            val base = if (configuredPath.isBlank()) {
                Files.createTempDirectory(TEMP_PREFIX)
            } else {
                Path.of(configuredPath)
            }
            return Files.createDirectories(base.resolve(identity.value))
        }
    }
```

- [ ] **Step 9: Run the embedded test and confirm it passes**

```bash
mvn -o -pl chat-core,chat-vector-embedded -Dtest=EmbeddedStorageDirectoryTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, 4 tests.

- [ ] **Step 10: Write the embedded wiring proof**

The tests so far call the companion functions. A hardcoded legacy name inside
either bean would pass every one of them. This step proves that the bean calls
the function.

Add this test to
`chat-vector-embedded/src/test/kotlin/com/demo/chat/test/vector/embedded/EmbeddedStorageDirectoryTests.kt`.

```kotlin
    @Test
    fun `the collection bean stores under the identity directory`() {
        // The tests above call the companion function. This test builds the
        // bean. A hardcoded path inside the bean would pass the others and
        // fail this one.
        val base = Files.createTempDirectory("embedded-wiring-test")

        ApplicationContextRunner()
            .withPropertyValues(
                "app.service.core.vector=embedded",
                "app.service.core.vector.embedded.path=$base",
            )
            .withBean(EmbeddingModel::class.java, { DummyEmbeddingModel() })
            .withBean(EmbeddingIdentity::class.java, { EmbeddingIdentity("wired-v1") })
            .withUserConfiguration(EmbeddedVectorStoreConfiguration::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(Files.isDirectory(base.resolve("wired-v1")))
                    .describedAs("the bean must store under the identity directory")
                    .isTrue()
            }
    }
```

Add these imports to that file.

```kotlin
import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.runner.ApplicationContextRunner
```

- [ ] **Step 11: Write the redis wiring proof**

Add this test to
`chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorStoreConfigurationTests.kt`.
That class already starts a Redis Stack container, and it carries
`@Tag("integration")`.

```kotlin
    @Test
    fun `the store bean writes under the identity prefix`() {
        // The name tests call the companion functions. This test builds the
        // bean and writes one document. A hardcoded index name inside the bean
        // would pass the name tests and fail this one.
        val identity = "wired-" + UUID.randomUUID().toString().take(8)

        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "test",
                mapOf(
                    "app.service.core.vector" to "redis",
                    "app.key.type" to "long",
                    "spring.redis.host" to stack.host,
                    "spring.redis.port" to stack.firstMappedPort.toString(),
                )
            )
        )
        context.beanFactory.registerSingleton("embeddingModel", DummyEmbeddingModel())
        context.beanFactory.registerSingleton("embeddingIdentity", EmbeddingIdentity(identity))
        context.register(RedisVectorStoreConfiguration::class.java)
        context.refresh()

        try {
            context.getBean(VectorStore::class.java)
                .add(listOf(messageDoc(1L, 20L, 10L, "apple pie recipe")))

            val jedis = JedisPooled(stack.host, stack.firstMappedPort)
            val keys = jedis.keys("chat:vector:long:$identity:message:*")

            Assertions.assertThat(keys)
                .describedAs("the bean must write under the identity prefix")
                .isNotEmpty()
        } finally {
            context.close()
        }
    }
```

Add this import to that file.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

- [ ] **Step 12: Change the two tests that the new bean parameters break**

Both changes are exact. No other test in this repository needs one.

Modify
`chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorStoreConfigurationTests.kt`.
The test named `configuration creates the store bean for vector redis` builds
the configuration, so it needs the new bean. Add this line directly after the
`embeddingModel` registration.

```kotlin
        context.beanFactory.registerSingleton("embeddingIdentity", EmbeddingIdentity("acme-e5"))
```

The private `store()` helper in that class builds a `RedisVectorStore` inline
and never reads the configuration. It needs no change.

`chat-vector-simple` needs no change. `SimpleVectorStoreConfiguration` takes no
identity.

- [ ] **Step 13: Run both modules in full**

```bash
mvn -o -pl chat-core,chat-vector-embedded clean test
mvn -o -pl chat-core,chat-vector-redis clean test
mvn -o -pl chat-core,chat-vector-redis -Pintegration clean verify
```

Run each command on its own. A module build halts at the first failing module,
and a combined run would hide a later failure. The third command needs Docker,
because the redis wiring proof carries `@Tag("integration")`.

Expected: PASS on all three.

- [ ] **Step 14: Prove each wiring test catches a hardcoded name**

Change `EmbeddedVectorStoreConfiguration.storageDirectoryFor` to ignore its
`identity` argument. Return `base.resolve("messages")` instead.

```bash
mvn -o -pl chat-core,chat-vector-embedded -Dtest=EmbeddedStorageDirectoryTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL, in `the collection bean stores under the identity directory`
and in the three companion tests.

Restore the function. Change the bean instead. Replace
`storageDirectoryFor(configuredPath, identity)` with
`storageDirectoryFor(configuredPath, EmbeddingIdentity("messages"))`.

```bash
mvn -o -pl chat-core,chat-vector-embedded -Dtest=EmbeddedStorageDirectoryTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL, in `the collection bean stores under the identity directory`
only. The three companion tests still pass, which is the point of this step.

Restore the bean. Run the command again and confirm every test passes.

Repeat the second mutation on the redis bean. Replace
`indexNameFor(keyType, identity)` and `prefixFor(keyType, identity)` with the
literal strings `"chat:vector:$keyType:message"` and
`"chat:vector:$keyType:message:"`. Run the integration verify and confirm that
`the store bean writes under the identity prefix` fails while
`RedisVectorNamesTests` passes. Restore both calls.

Record both results in the commit message.

- [ ] **Step 15: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add chat-vector-redis chat-vector-embedded
git commit -F - <<'MSG'
feat: carry the embedding identity into both vector stores (CHAT-etfnihnu)

The redis index name and the embedded collection directory each carry the
embedding identity. A metadata field does not isolate a redis index, and
two models rarely share a vector width.

Every index and every directory that an earlier build wrote orphans. Their
names carry no identity segment. The corpus is a derived cache, so a
rebuild replaces it.

chat-vector-embedded gained a compile scope chat-core dependency. It
declared that artifact only as a test jar, and it cannot read
EmbeddingIdentity without one.

Evidence, measured on <DATE>.

- <N> tests pass in RedisVectorNamesTests.
- <N> tests pass in EmbeddedStorageDirectoryTests.
- <N> tests pass in RedisVectorStoreConfigurationTests, under -Pintegration.
- Mutation one: an identity that storageDirectoryFor ignored failed the
  wiring test and the three companion tests.
- Mutation two: a bean that passed a fixed identity failed the wiring test
  alone. The three companion tests passed, which is why the wiring test
  exists.
- Mutation three: a literal redis index name failed the redis wiring test.
  RedisVectorNamesTests passed.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 5: The identity reaches the job record and coverage

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/IndexJob.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorIndexJobStoreImpl.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorCoveragePolicyImpl.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`
- Modify: `chat-core/src/test/kotlin/com/demo/chat/test/vector/IndexJobCodecTests.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorCoveragePolicyImplTests.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorIndexJobStoreImplTests.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt`

**Interfaces:**
- Consumes: `com.demo.chat.domain.EmbeddingIdentity` from Task 1.
- Produces:
  - `IndexJob.embeddingIdentity: String?`, which defaults to null.
  - `VectorIndexJobStoreImpl(..., embeddingIdentity: EmbeddingIdentity, ...)`.
  - `VectorCoveragePolicyImpl(..., embeddingIdentity: EmbeddingIdentity, ...)`.

- [ ] **Step 1: Add the field to IndexJob**

Modify `chat-core/src/main/kotlin/com/demo/chat/domain/IndexJob.kt`. Add one
argument after `keyType`.

```kotlin
    val keyType: String,
    /**
     * The model that wrote this corpus, as the operator named it.
     *
     * The field is nullable. A record written before this field existed
     * carries null, which states that the writer named no model. An empty
     * string would state that the writer named an empty model, and those are
     * different facts.
     */
    val embeddingIdentity: String? = null,
```

Place it after `keyType` and before `incarnationId`. Every construction in
this repository uses named arguments, so the position breaks no caller.

- [ ] **Step 2: Run the whole reactor and confirm nothing breaks**

```bash
mvn -o -B clean test
```

Expected: BUILD SUCCESS. The field has a default, so no caller changes yet.

- [ ] **Step 3: Write the failing job store test**

Modify
`chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorIndexJobStoreImplTests.kt`.

Add the import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

Add one test.

```kotlin
    @Test
    fun `a new job carries the identity of this process`() {
        val store = storeUnderTest()

        StepVerifier.create(store.createJob(Instant.now()))
            .assertNext { job ->
                assertThat(job.embeddingIdentity).isEqualTo("acme-e5-small-v2")
            }
            .verifyComplete()
    }
```

Change the `storeUnderTest` helper at line 191. Add the new argument.

```kotlin
            embeddingIdentity = EmbeddingIdentity("acme-e5-small-v2"),
```

- [ ] **Step 4: Run the test and confirm it fails**

```bash
mvn -o -pl chat-core,chat-service-composite -Dtest=VectorIndexJobStoreImplTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: a compile failure. The constructor takes no `embeddingIdentity`.

- [ ] **Step 5: Change the job store**

Modify
`chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorIndexJobStoreImpl.kt`.

Add the import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

Add the constructor argument after `keyType`.

```kotlin
    private val keyType: String,
    private val embeddingIdentity: EmbeddingIdentity,
```

Write the value on a new job.

```kotlin
                val job = IndexJob(
                    key = key,
                    nodeId = nodeId,
                    keyType = keyType,
                    embeddingIdentity = embeddingIdentity.value,
                    incarnationId = incarnationId,
                    startedBy = workerKey,
                    startedAt = startedAt,
                )
```

Only `createJob` writes the value. `finishJob` copies a job that already
carries it, and `invalidate` copies a stored job.

- [ ] **Step 6: Run the job store test and confirm it passes**

```bash
mvn -o -pl chat-core,chat-service-composite -Dtest=VectorIndexJobStoreImplTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Write the failing coverage tests**

Modify
`chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorCoveragePolicyImplTests.kt`.

Add the import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

Change the policy construction at line 40.

```kotlin
        VectorCoveragePolicyImpl(
            store,
            trust,
            thisIncarnation,
            nodeId = 7,
            keyType = "long",
            embeddingIdentity = EmbeddingIdentity("acme-e5"),
            typeUtil = LongUtil(),
        )
```

Every existing job that this class builds must now carry
`embeddingIdentity = "acme-e5"`. Add that named argument to each construction,
or the existing tests will report no coverage.

Change the private `job` helper. Add one parameter with a default that matches
the policy identity, so every existing test keeps covering.

```kotlin
    private fun job(
        id: Long,
        outcome: JobOutcome = JobOutcome.SUCCEEDED,
        incarnationId: String = thisIncarnation,
        invalidations: Long = 0L,
        startedAt: Instant = start,
        embeddingIdentity: String? = thisIdentity.value,
    ): IndexJob<Long> = IndexJob(
        key = Key.funKey(id),
        nodeId = 7,
        keyType = "long",
        embeddingIdentity = embeddingIdentity,
        incarnationId = incarnationId,
        startedBy = Key.funKey(1000L),
        startedAt = startedAt,
        outcome = outcome,
        invalidationCount = invalidations,
    )
```

Add the identity beside the other fixtures at the top of the class.

```kotlin
    private val thisIdentity = EmbeddingIdentity("acme-e5")
```

Add three tests. Each one uses the `store` and the `policy` helper that this
class already carries.

```kotlin
    @Test
    fun `a job of another identity does not cover`() {
        store.write(job(1L, embeddingIdentity = "other-model")).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `a job written before this change does not cover`() {
        // A legacy record carries null. Null names no model, and the vectors
        // it wrote came from a model that has no name.
        store.write(job(1L, embeddingIdentity = null)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `a newer job of a foreign identity does not hide a current covering job`() {
        // The identity filter runs before the sort and before next(). A newer
        // foreign job would otherwise reach next() first. The policy would
        // select it, reject it, and report no coverage. The valid older job
        // would then be hidden, and the index would rebuild for no reason.
        store.write(job(1L, startedAt = start)).block()
        store.write(
            job(2L, startedAt = start.plusSeconds(60), embeddingIdentity = "other-model")
        ).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .assertNext { found -> Assertions.assertThat(found.key.id).isEqualTo(1L) }
            .verifyComplete()
    }
```

Add this import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

- [ ] **Step 8: Run the coverage test and confirm it fails**

```bash
mvn -o -pl chat-core,chat-service-composite -Dtest=VectorCoveragePolicyImplTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL. The three new tests each select a job that must not cover.

- [ ] **Step 9: Change the coverage policy**

Modify
`chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorCoveragePolicyImpl.kt`.

Add the import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

Add the constructor argument after `keyType`.

```kotlin
    private val keyType: String,
    private val embeddingIdentity: EmbeddingIdentity,
```

Add the filter directly after the `SUCCEEDED` filter and before the `sort`.

```kotlin
            .filter { job -> job.outcome == JobOutcome.SUCCEEDED }
            // This filter runs before the sort and before next(), and that
            // order is the rule. A newer job of a foreign identity would
            // otherwise reach next() first. The policy would select it, reject
            // it, and report no coverage. A valid older job of this identity
            // would then be hidden behind it, and the index would rebuild for
            // no reason.
            //
            // A legacy record carries null, which never equals an identity
            // value. So the first start after this change reports an
            // incomplete index and waits for a rebuild. That outcome is
            // correct, because those vectors came from a model that no longer
            // has a name.
            .filter { job -> job.embeddingIdentity == embeddingIdentity.value }
            .filter { job -> trust == VectorTrust.STORED || job.incarnationId == incarnationId }
```

- [ ] **Step 10: Run the coverage test and confirm it passes**

```bash
mvn -o -pl chat-core,chat-service-composite -Dtest=VectorCoveragePolicyImplTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 11: Prove the filter order with a mutation**

Move the identity filter to after `.next()`. Run the test again.

```bash
mvn -o -pl chat-core,chat-service-composite -Dtest=VectorCoveragePolicyImplTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL, in
`a newer job of a foreign identity does not hide a current covering job`.

Restore the filter to its place before the sort. Run the test again and
confirm it passes. Record the mutation result in the commit message.

A test that passes under both placements proves nothing about the order. Fix
the test in that case, and repeat this step.

- [ ] **Step 12: Prove a legacy record decodes with a null identity**

A stored job that an earlier build wrote carries no `embeddingIdentity` field.
The coverage filter rests on that record decoding to null rather than failing
the read. Each backend hands the codec a different shape, so prove both.

Add these tests to
`chat-core/src/test/kotlin/com/demo/chat/test/vector/IndexJobCodecTests.kt`.

```kotlin
    @Test
    fun `a legacy json string decodes with a null identity`() {
        // A record written before the field existed carries no key for it.
        // Null states that the writer named no model. That is the fact the
        // coverage filter reads.
        //
        // The fixture is derived rather than written by hand. This mapper
        // already round trips `job` in the test above, so removing one field
        // from its own output cannot disagree with the real wire shape. A
        // hand written literal could, because Key carries a wrapper object and
        // the path to an id is one level deeper than its field name.
        val legacy = mapper.writeValueAsString(withoutIdentity())

        Assertions.assertThat(codec.decode(legacy).embeddingIdentity).isNull()
    }

    @Test
    fun `a legacy map decodes with a null identity`() {
        Assertions.assertThat(codec.decode(withoutIdentity()).embeddingIdentity).isNull()
    }

    /**
     * The map shape of [job], with the new field removed.
     *
     * Redis hands the codec a map after its JSON round trip, and cassandra
     * hands it the JSON string. So one fixture serves both tests.
     */
    private fun withoutIdentity(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val asMap = mapper.convertValue(job, Map::class.java) as Map<String, Any?>
        return asMap.filterKeys { it != "embeddingIdentity" }
    }
```

The fixture removes the field from the mapper's own output. So it needs no
hand written JSON, and it cannot drift from the real wire shape.

Set `embeddingIdentity = "acme-e5"` on the `job` fixture at the top of the
class first. The field must be present in the output before a test can remove
it.

- [ ] **Step 13: Run the codec tests**

```bash
mvn -o -pl chat-core -Dtest=IndexJobCodecTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 14: Pass the identity through the wiring**

Do this step as soon as Step 5 changes the job store constructor.
`VectorRecallServiceConfiguration` calls both constructors, so
`chat-service-composite` does not compile between Step 5 and this step. The
step stays here in the plan for its explanation.

Modify
`chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`.

Add the import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

Change `vectorIndexJobStore`. Add the parameter and pass it.

```kotlin
    fun vectorIndexJobStore(
        embeddingIdentity: EmbeddingIdentity,
        @Value("\${app.nodeid}") nodeId: Int,
        @Value("\${app.key.type}") keyType: String,
    ): VectorIndexJobStoreImpl<T, V, Q> {
```

```kotlin
            nodeId = nodeId,
            keyType = keyType,
            embeddingIdentity = embeddingIdentity,
            incarnationId = incarnationId,
            workerKey = workerKey,
```

Change `vectorCoveragePolicy` the same way.

```kotlin
    fun vectorCoveragePolicy(
        jobStore: VectorIndexJobStore<T>,
        embeddingIdentity: EmbeddingIdentity,
        @Value("\${app.nodeid}") nodeId: Int,
        @Value("\${app.key.type}") keyType: String,
        @Value("\${app.vector.index.trust:none}") trust: String,
    ): VectorCoveragePolicy<T> =
        VectorCoveragePolicyImpl(
            jobStore = jobStore,
            trust = trustOf(trust),
            incarnationId = incarnationId,
            nodeId = nodeId,
            keyType = keyType,
            embeddingIdentity = embeddingIdentity,
            typeUtil = typeUtil,
        )
```

Both beans already carry
`@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])`.
`EmbeddingIdentityConfiguration` carries the same condition, so the bean is
present whenever these beans are.

- [ ] **Step 15: Change the one test that the new bean parameters break**

The change is exact. One test class in this repository builds
`VectorRecallServiceConfiguration` directly, and it needs the new bean.

Modify
`chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt`.
Add this line to `contextWith`, directly after the `vectorStore` registration.

```kotlin
        context.beanFactory.registerSingleton("embeddingIdentity", EmbeddingIdentity.MOCK)
```

Add this import.

```kotlin
import com.demo.chat.domain.EmbeddingIdentity
```

`allProperties` in that class sets `app.service.core.embedding=mock`, which
resolves the fixed identity. So `EmbeddingIdentity.MOCK` is the right value.

**No other test needs a change.** These are the measured reasons.

- `VectorIndexEndpointTests` uses a runner that registers a
  `VectorIndexJobStore` double and `VectorIndexEndpoint` itself. It never builds
  `VectorRecallServiceConfiguration`, so no bean of this configuration resolves
  there.
- `MemoryVectorRecallBootTests`, `MemoryEmbeddedVectorRecallBootTests`,
  `MemoryVectorIndexActuatorTests`, `VectorIndexRecoveryTests`, and
  `RedisVectorRecallBootTests` each set `app.service.core.embedding=mock` and
  boot `ChatApp`. `ChatApp` declares
  `scanBasePackages = ["com.demo.chat.config"]`, and
  `EmbeddingIdentityConfiguration` sits in that package. So the bean builds and
  resolves `EmbeddingIdentity.MOCK` with no property.
- `SimpleVectorStoreConfigurationTests` builds a store that takes no identity.

- [ ] **Step 16: Run the module and both deployments**

```bash
mvn -o -pl chat-core,chat-service-composite clean test
mvn -o -pl chat-deploy-memory -am clean test
mvn -o -pl chat-deploy-redis -am clean test
```

Run each command on its own. A module build halts at the first failing module,
and a combined run would hide a later failure.

Each deployment command needs `-am`. Both deployments read
`chat-service-composite`, which this task changes. A list of
`chat-core,chat-deploy-memory` resolves the composite from `~/.m2` instead.
Measured on 2026-09-14: that narrow list gave three timeouts in
`VectorIndexRecoveryTests`, and the cause was
`NoSuchMethodError: IndexJob.<init>` from the stale jar. With `-am` the same
three tests pass in 10.3 seconds.

Expected: PASS on all three.

- [ ] **Step 17: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add chat-core/src/main/kotlin/com/demo/chat/domain/IndexJob.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/vector/IndexJobCodecTests.kt \
        chat-service-composite
git commit -F - <<'MSG'
feat: record and match the embedding identity on every job (CHAT-etfnihnu)

Every new job records the identity of the model that wrote it. Coverage
selects a job only when its identity matches the one this process resolved.

The filter runs before the sort and before next(). A newer job of a foreign
identity would otherwise reach next() first, and a valid older job of this
identity would hide behind it.

A record written before this change carries null. Null names no model, so
the first start after this change reports an incomplete index and waits for
a rebuild. Those vectors came from a model that no longer has a name.

Evidence, measured on <DATE>.

- <N> tests pass in VectorCoveragePolicyImplTests.
- <N> tests pass in VectorIndexJobStoreImplTests.
- <N> tests pass in IndexJobCodecTests, including two legacy decodes.
- Mutation: the identity filter moved after next() failed the test named
  'a newer job of a foreign identity does not hide a current covering job'.
- One test class needed an edit. VectorRecallServiceConfigurationTests now
  registers the identity bean. Five boot tests needed none, because each
  sets embedding=mock and ChatApp scans com.demo.chat.config.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 6: The deployments declare the providers

**Files:**
- Modify: `chat-deploy-memory/pom.xml`
- Modify: `chat-deploy-redis/pom.xml`

**Interfaces:**
- Consumes: both provider modules from Task 2 and Task 3.
- Produces: nothing new. Both deployments can now select any of the three
  embedding values.

- [ ] **Step 1: Declare both providers in the memory deployment**

Modify `chat-deploy-memory/pom.xml`. Add both entries beside the vector module
entries.

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

- [ ] **Step 2: Move the memory test jar to test scope**

Modify `chat-deploy-memory/pom.xml` at lines 79 to 84. Add the scope element.

```xml
        <dependency>
            <groupId>com.demo</groupId>
            <artifactId>chat-core</artifactId>
            <type>test-jar</type>
            <version>0.0.1</version>
            <scope>test</scope>
        </dependency>
```

This is the change the whole issue exists for. The mock then reaches tests and
never reaches a launch.

- [ ] **Step 3: Install the reactor, so a standalone resolution is correct**

```bash
mvn -o -B -Dmaven.test.skip=true clean install
```

Expected: BUILD SUCCESS.

A `-pl <module>` run resolves every `com.demo` dependency from `~/.m2`. Both
provider modules are new, and `chat-deploy-memory` now depends on them. An
uninstalled reactor makes the next step read a stale jar or fail on a missing
one. See `forward-register.md`, the row that names the stale artifact trap.

- [ ] **Step 4: Prove the runtime classpath carries no test jar**

```bash
CP=$(mktemp)
mvn -o -q -pl chat-deploy-memory \
    -DincludeScope=runtime -Dmdep.outputFile="$CP" \
    dependency:build-classpath || { echo "resolution failed"; exit 1; }
if grep -q 'tests\.jar' "$CP"; then
    echo "a test jar is on the runtime classpath:"
    tr ':' '\n' < "$CP" | grep 'tests\.jar'
    exit 1
fi
echo "ok, no test jar on the runtime classpath"
```

Expected: `ok, no test jar on the runtime classpath`.

Use `grep -q` inside an `if`. A bare `grep -c` exits with status 1 when it
counts zero, so the success case would read as a failure.

Before this change the same command found `chat-core-0.0.1-tests.jar`.

- [ ] **Step 5: Run the memory deployment tests**

```bash
mvn -o -pl chat-core,chat-deploy-memory clean test
```

Expected: PASS. The test classpath still carries the test jar, so
`DummyEmbeddingModel` still reaches every test.

A compile failure in `src/main` means a main source read a test type. Fix the
main source. Do not widen the scope back.

- [ ] **Step 6: Declare both providers in the redis deployment**

Modify `chat-deploy-redis/pom.xml`. Add both entries beside
`chat-vector-redis`.

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

`chat-deploy-redis` already declares its `chat-core` test jar at test scope. It
needs no scope change.

- [ ] **Step 7: Run the redis deployment tests**

```bash
mvn -o -pl chat-core,chat-deploy-redis clean test
```

Expected: PASS.

- [ ] **Step 8: Run the whole reactor**

```bash
mvn -o -B clean test
```

Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add chat-deploy-memory/pom.xml chat-deploy-redis/pom.xml
git commit -F - <<'MSG'
feat: declare both embedding providers in both deployments (CHAT-etfnihnu)

Both deployments declare both production embedding modules at compile
scope. chat-deploy-memory also moves its chat-core test jar to test scope.

That scope is the defect this issue exists for. The jar carried the only
EmbeddingModel in this repository onto a launched classpath. No test could
detect it, because a Spring Boot test puts test jars on its classpath by
construction.

Evidence, measured on <DATE>.

- The runtime classpath of chat-deploy-memory carries no file whose name
  ends tests.jar. It carried chat-core-0.0.1-tests.jar before this change.
- <N> tests pass in chat-deploy-memory.
- <N> tests pass in chat-deploy-redis.
- The full reactor reports <N> modules SUCCESS.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 7: The production dependency guard

**Prerequisite:** `CHAT-xvtsffqh` must be done. Nine modules declare a test jar
with no scope element, and rule one fails on each of them. Task 6 moved the
tenth.

**Files:**
- Create: `shell-scripts/check-production-classpath.sh`
- Modify: `justfile`

This task changes no pom. `CHAT-xvtsffqh` owns every scope change that rule one
or rule two reports, `chat-shell` included. Task 6 already moved the one module
that this work owns.

**Interfaces:**
- Consumes: the scope change from Task 6.
- Produces: `./shell-scripts/check-production-classpath.sh`, and
  `just check-production-classpath`. Exit status 0 means no breach.

- [ ] **Step 1: Confirm the prerequisite is done**

```bash
FP_AGENT_NAME='sigma' fp issue show CHAT-xvtsffqh | head -5
```

Expected: `Status: done`.

Stop this task if the status is not done. Rule one fails on nine modules that
this work does not own.

- [ ] **Step 2: Write the guard script**

Create `shell-scripts/check-production-classpath.sh`.

```bash
#!/bin/bash
#
# Fails when a test artifact can reach a production classpath.
#
# The rule exists because a test jar carried the only EmbeddingModel in this
# repository onto a launched deployment. chat-deploy-memory declared the
# chat-core test jar with no scope element, so it resolved at compile. The
# feature ran in every test and would have failed at launch. No test could
# detect it, because a Spring Boot test puts test jars on its classpath by
# construction. See CHAT-etfnihnu.
#
# Rule one reads the poms. Every dependency with <type>test-jar</type> must
# declare <scope>test</scope>.
#
# Rule two reads the resolved classpath. A known test library must not appear
# on a compile or runtime classpath. A transitive leak never appears in a pom,
# which is why rule two resolves rather than reads. Rule two closes
# CHAT-incuynpc, where two testcontainers artifacts resolve at compile scope in
# chat-shell. Those are ordinary jars and not test jars, so rule one cannot see
# them.
#
#   ./shell-scripts/check-production-classpath.sh
#
# Exit status: 0 when no module breaches either rule, 1 when one does.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/.." && pwd )"
cd "$ROOT" || exit 1

# CLAUDE.md requires miniforge for Python. Both rules read their input with
# Python, so the activation happens once, here, and it must succeed.
# shellcheck disable=SC1090
if [ -f "$HOME/miniforge3/etc/profile.d/conda.sh" ]; then
    source "$HOME/miniforge3/etc/profile.d/conda.sh"
    conda activate base || { echo "conda activate base failed"; exit 1; }
else
    echo "miniforge is not installed at ~/miniforge3."
    echo "CLAUDE.md requires miniforge for Python. Ask the owner for guidance."
    exit 1
fi
command -v python3 > /dev/null || { echo "no python3 after conda activate base"; exit 1; }

FAILED=0

echo "Rule one: every test-jar dependency declares test scope."
python3 - <<'PY'
import re, glob, sys

violations = []
for path in sorted(glob.glob('**/pom.xml', recursive=True)):
    if '/target/' in path:
        continue
    text = re.sub(r'<!--.*?-->', '', open(path).read(), flags=re.S)
    text = re.sub(r'<build>.*?</build>', '', text, flags=re.S)
    for block in re.finditer(r'<dependency>(.*?)</dependency>', text, re.S):
        body = block.group(1)
        if '<type>test-jar</type>' not in body:
            continue
        if re.search(r'<scope>\s*test\s*</scope>', body):
            continue
        artifact = re.search(r'<artifactId>([^<]*)</artifactId>', body)
        artifact = artifact.group(1) if artifact else '?'
        violations.append((path, artifact))

if violations:
    print()
    print('A test-jar dependency declares no test scope:')
    print()
    for path, artifact in violations:
        print(f'  {path}')
        print(f'    {artifact}, type test-jar')
    print()
    print('Add <scope>test</scope>. A test jar on a production classpath ships')
    print('test code to a launch, and no test can detect it.')
    sys.exit(1)
print('  ok')
PY
[ $? -ne 0 ] && FAILED=1

echo
echo "Rule two: no known test library on a runtime classpath."

MODULES=$(python3 -c "
import re
text = open('pom.xml').read()
for m in re.findall(r'<module>([^<]*)</module>', text):
    print(m)
")

CPDIR=$(mktemp -d)
trap 'rm -rf "$CPDIR"' EXIT

# A -pl run resolves every com.demo dependency from ~/.m2. So the reactor must
# be installed before this rule reads a single module. An uninstalled or stale
# local repository makes this rule read the wrong classpath, or none at all.
echo "  installing the reactor, so each standalone resolution is correct"
if ! mvn -o -B -Dmaven.test.skip=true install > "$CPDIR/install.log" 2>&1; then
    tail -40 "$CPDIR/install.log"
    echo
    echo "The install did not finish. Rule two cannot resolve a classpath."
    exit 1
fi

for module in $MODULES; do
    out="$CPDIR/$module.txt"
    log="$CPDIR/$module.log"
    # Never hide the Maven output. A silent resolution failure writes no file,
    # and a rule that reads no file reports no violation. That is a false pass.
    if ! mvn -o -pl "$module" \
        -DincludeScope=runtime \
        -Dmdep.outputFile="$out" \
        dependency:build-classpath > "$log" 2>&1; then
        tail -30 "$log"
        echo
        echo "Resolution failed for $module. Rule two cannot run."
        exit 1
    fi
    # A module with no runtime dependency writes no file. That is a legal
    # outcome, and an empty file makes the reader treat it as one.
    [ -f "$out" ] || : > "$out"
done

python3 - "$CPDIR" <<'PY'
import os, sys

# Each entry matches a path segment of a resolved artifact. A group id maps to
# a directory path under the local repository. An artifact id matches the jar
# name.
FORBIDDEN = [
    'org/testcontainers/',
    'com/redis/testcontainers-redis/',
    'org/junit/jupiter/',
    'org/junit/platform/',
    'org/mockito/',
    'org/assertj/',
    'io/projectreactor/reactor-test/',
    'org/springframework/boot/spring-boot-starter-test/',
    'org/springframework/security/spring-security-test/',
]

directory = sys.argv[1]
violations = []
# Only the classpath outputs. The same directory holds install.log and one
# .log per module, and a Maven log names every artifact it downloads. Reading
# those would report a violation for a test library that no classpath carries.
for name in sorted(n for n in os.listdir(directory) if n.endswith('.txt')):
    module = name[:-4]
    entries = open(os.path.join(directory, name)).read().split(os.pathsep)
    for entry in entries:
        normal = entry.replace(os.sep, '/')
        for forbidden in FORBIDDEN:
            if forbidden in normal:
                violations.append((module, forbidden, os.path.basename(entry)))

if violations:
    print()
    print('A test library resolves on a runtime classpath:')
    print()
    for module, forbidden, jar in violations:
        print(f'  {module}')
        print(f'    {jar}, matched {forbidden}')
    print()
    print('Add <scope>test</scope> to the declaration, or remove the')
    print('dependency that drags it in.')
    sys.exit(1)
print('  ok')
PY
[ $? -ne 0 ] && FAILED=1

echo
if [ "$FAILED" -eq 0 ]; then
    echo "No module breaches either rule."
else
    echo "At least one module breaches a rule. See above."
fi
exit "$FAILED"
```

Note the `org/mockito/` entry. It covers both `org.mockito` and
`org.mockito.kotlin`, because both resolve under that path.

- [ ] **Step 3: Make the script runnable**

```bash
chmod +x shell-scripts/check-production-classpath.sh
```

- [ ] **Step 4: Run the guard and confirm it passes**

```bash
./shell-scripts/check-production-classpath.sh
```

Expected: exit status 0. Both rules report `ok`.

**Stop this task on any failure, and change no pom here.** A rule one or rule
two failure names a module that `CHAT-xvtsffqh` owns. That issue carries the two
`chat-shell` testcontainers declarations, and closing `CHAT-incuynpc` is its
work rather than this task's.

Report the failure on `CHAT-xvtsffqh` with the module name and the artifact.

```bash
FP_AGENT_NAME='sigma' fp comment CHAT-xvtsffqh "The production classpath guard \
reports <module>, artifact <artifact>. Rule <one or two> named it. This issue \
owns that scope change. CHAT-etfnihnu Task 7 installs the guard and changes no \
pom."
```

Then wait. This task installs the rule that keeps the breach from returning. It
does not repair a breach that another issue owns.

- [ ] **Step 5: Prove rule one catches a breach**

Remove `<scope>test</scope>` from the `chat-core` test jar entry in
`chat-deploy-memory/pom.xml`. Run the guard.

```bash
./shell-scripts/check-production-classpath.sh
```

Expected: exit status 1. The output names `chat-deploy-memory/pom.xml`.

Restore the scope element. Run the guard again and confirm exit status 0.

- [ ] **Step 6: Prove rule two catches a breach**

Remove `<scope>test</scope>` from the `spring-boot-starter-test` entry in
`chat-embedding-openai/pom.xml`. Run the guard.

```bash
./shell-scripts/check-production-classpath.sh
```

Expected: exit status 1. The output names `chat-embedding-openai` and
`spring-boot-starter-test`.

Restore the scope element. Run the guard again and confirm exit status 0.

Rule one does not catch this breach. `spring-boot-starter-test` is an ordinary
jar and not a test jar. That difference is why two rules exist.

- [ ] **Step 7: Add the recipe**

Modify `justfile`. Add the recipe beside `check-deps`.

```
check-production-classpath:
	./shell-scripts/check-production-classpath.sh
```

- [ ] **Step 8: Run the recipe**

```bash
just check-production-classpath
```

Expected: exit status 0.

- [ ] **Step 9: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add shell-scripts/check-production-classpath.sh justfile
git commit -F - <<'MSG'
feat: guard production classpaths against test artifacts (CHAT-etfnihnu)

One script holds two rules, and both run over every module.

Rule one reads the poms. Every test-jar dependency must declare test scope.
Rule two reads the resolved runtime classpath. A known test library must
not appear on it. A transitive leak never appears in a pom, which is why
rule two resolves rather than reads.

The script changes no pom. CHAT-xvtsffqh owns every scope change that
either rule reports.

Evidence, measured on <DATE>.

- The guard exits 0 over all <N> modules.
- Mutation one: chat-deploy-memory without test scope on its test jar
  failed rule one. The script named the pom.
- Mutation two: chat-embedding-openai without test scope on
  spring-boot-starter-test failed rule two. The script named the module and
  the artifact. Rule one passed, because that artifact is an ordinary jar.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 8: The dead endpoint proofs

The spec states three failure moments, and each rests on when a vector store
calls `EmbeddingModel.dimensions()`. This task turns each statement into a
test. The owner asked for automated checks rather than manual procedures.

| Vector store | When it calls `dimensions()` | Failure moment |
|--------------|------------------------------|----------------|
| `embedded` | when the collection bean builds | startup, always |
| `redis` | when it creates an index that is absent | startup, or later |
| `simple` | never, at build time | the first vector operation |

**Files:**
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/DeadEmbeddingEndpointTests.kt`
- Create: `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisDeadEmbeddingEndpointTests.kt`

This task changes no pom.

**Interfaces:**
- Consumes: Task 6. Both deployments declare both provider modules, so an
  `openai` selector resolves a real client in each test classpath.
- Produces: no production code.

Each test points the base URL at a closed loopback port. So each test needs no
external network and no secret key. A closed loopback port refuses at once.

**Each launch must also set `app.service.core.embedding.openai.max-attempts=1`.**
The connection is refused at once, but the default retry policy makes 10
attempts and waits between them. Measured on 2026-09-14: that policy is
`RetryUtils.DEFAULT_RETRY_TEMPLATE`, which is 10 attempts, a 2 second first
wait, a multiplier of 5, and a 180 second cap. It needs near 10 minutes to give
up on one call, and the two memory tests are not tagged, so the default build
would carry 20 minutes. Task 2 gained the property for this reason.

Each class below carries its own `closedPort()`. It opens a
`java.net.ServerSocket(0)`, reads `localPort`, and closes the socket. A port
that the helper closes stays closed for the test that follows. `CLAUDE.md`
forbids a text search for a symbol, so this plan writes the helper rather than
telling the implementer to find one.

- [ ] **Step 1: Write the embedded and simple proofs**

Create
`chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/DeadEmbeddingEndpointTests.kt`.

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.builder.SpringApplicationBuilder
import java.net.ConnectException
import java.net.ServerSocket

/**
 * An unreachable embedding endpoint fails at a different moment for each
 * vector store. EmbeddingModel.dimensions() is the reason. Spring AI can reach
 * the remote service to answer it, and each store asks at a different time.
 *
 * Every launch here points at a closed loopback port, so no test needs an
 * external network or a secret key. A closed loopback port refuses at once.
 *
 * SpringApplicationBuilder.properties() writes to defaultProperties, which is
 * the lowest precedence source. So each value below is a command line
 * argument. See forward-register.md.
 */
class DeadEmbeddingEndpointTests {

    private fun closedPort(): Int =
        ServerSocket(0).use { socket -> socket.localPort }

    private fun launchArguments(vector: String, port: Int): Array<String> = arrayOf(
        "--app.nodeid=1",
        "--app.key.type=long",
        "--spring.application.name=dead-endpoint-$vector",
        "--app.server.proto=rsocket",
        "--spring.rsocket.server.port=0",
        "--spring.config.additional-location=classpath:/config/logging.yml," +
            "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "--app.service.core.key=memory",
        "--app.service.core.persistence=memory",
        "--app.service.core.index=lucene",
        "--app.service.core.pubsub=memory",
        "--app.service.core.secrets=memory",
        "--app.service.composite=true",
        "--app.service.composite.auth=true",
        "--app.service.security.userdetails=true",
        "--app.service.core.vector=$vector",
        "--app.service.core.embedding=openai",
        "--app.service.core.embedding.identity=dead-endpoint-v1",
        "--app.service.core.embedding.openai.base-url=http://127.0.0.1:$port",
        "--app.service.core.embedding.openai.api-key=not-a-secret",
        "--app.service.core.embedding.openai.model=stub-embedding",
    )

    @Test
    fun `an unreachable endpoint fails startup under embedded`() {
        // The collection bean takes its width from dimensions(), so the call
        // happens while the context refreshes.
        val port = closedPort()

        val thrown = catchThrowable {
            SpringApplicationBuilder(ChatApp::class.java)
                .run(*launchArguments("embedded", port))
                .close()
        }

        assertReachedTheEndpoint(thrown, port)
    }

    @Test
    fun `an unreachable endpoint starts under simple and fails the first operation`() {
        // SimpleVectorStore never calls dimensions() at build time. So the
        // context refreshes, and the failure waits for a vector operation.
        val port = closedPort()
        val context = SpringApplicationBuilder(ChatApp::class.java)
            .run(*launchArguments("simple", port))

        try {
            assertThat(context.isActive)
                .describedAs("the context must refresh under simple")
                .isTrue()

            val store = context.getBean(VectorStore::class.java)

            val thrown = catchThrowable {
                store.add(
                    listOf(
                        Document.builder()
                            .id("message:long:1")
                            .text("apple pie recipe")
                            .build()
                    )
                )
            }

            assertReachedTheEndpoint(thrown, port)
        } finally {
            context.close()
        }
    }

    /**
     * Asserts that the failure came from the endpoint and from nothing else.
     *
     * A bare `isNotNull` would pass on a missing bean, a bad property name, or
     * a port that another process holds. Each of those is a defect in the
     * test, not the behaviour under test. So this check reads the whole cause
     * chain and requires the closed port number in it, and it refuses a
     * missing bean.
     */
    private fun assertReachedTheEndpoint(thrown: Throwable?, port: Int) {
        assertThat(thrown).describedAs("expected a failure").isNotNull()

        val chain = generateSequence(thrown) { it.cause }.toList()
        val text = chain.joinToString(" | ") { "${it.javaClass.name}: ${it.message}" }

        assertThat(chain.map { it.javaClass.name })
            .describedAs("the failure must not be a missing bean: %s", text)
            .doesNotContain("org.springframework.beans.factory.NoSuchBeanDefinitionException")

        // The type, and not only the text. A port number can appear in a
        // message that a live server returned, so the text check alone would
        // pass on an HTTP 500. A refused TCP connection is always a
        // ConnectException. Netty's AnnotatedConnectException extends it, so
        // one check covers every client this repository builds.
        assertThat(chain.any { it is ConnectException })
            .describedAs("the failure must be a refused connection: %s", text)
            .isTrue()

        assertThat(text)
            .describedAs("the failure must name the closed endpoint")
            .contains(port.toString())
    }
}
```

The `embedded` test needs `--add-modules jdk.incubator.vector` on the surefire
JVM of `chat-deploy-memory`. **That argument is already there.**
`chat-deploy-memory/pom.xml:124` declares
`<argLine>--add-modules jdk.incubator.vector</argLine>`, with a comment that
records why. So this task adds nothing to that pom.

- [ ] **Step 2: Run the two proofs**

```bash
mvn -o -pl chat-core,chat-deploy-memory -Dtest=DeadEmbeddingEndpointTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, 2 tests.

A failure in the `simple` test that reports a refused context means
`SimpleVectorStore` does call `dimensions()` at build time. The spec's table is
then wrong. Record that as a spec correction and tell the owner. Do not change
the test to match.

- [ ] **Step 3: Write the redis proof**

Create
`chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisDeadEmbeddingEndpointTests.kt`.

```kotlin
package com.demo.chat.test.deploy.redis

import com.redis.testcontainers.RedisStackContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import java.net.ConnectException
import java.net.ServerSocket
import java.util.UUID

/**
 * Redis fails at startup only on a first run.
 *
 * Schema initialization skips dimensions() when the index already exists. A
 * new identity creates a new index name, so the first run under a new identity
 * does reach dimensions(). This test uses a fresh identity, so the index is
 * always absent.
 *
 * The launch values come from RedisVectorRecallBootTests, which is the boot
 * test of this backend. That class uses @SpringBootTest, which cannot express
 * a refused context. So this class drives SpringApplicationBuilder instead.
 *
 * SpringApplicationBuilder.properties() writes to defaultProperties, the
 * lowest precedence source, so every value below is a command line argument.
 * See forward-register.md.
 *
 * This test claims no node id. Key, persistence, index, and secrets all use
 * memory selectors, so no claim store activates. See docs/NODEID-CLAIM.md and
 * the same note on RedisVectorRecallBootTests.
 */
@Tag("integration")
class RedisDeadEmbeddingEndpointTests {

    companion object {
        val redisStack = RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG)
        ).apply { start() }
    }

    /** Mirrors the BootApp of RedisVectorRecallBootTests. */
    @SpringBootApplication(proxyBeanMethods = false, scanBasePackages = ["com.demo.chat.config"])
    class BootApp

    private fun closedPort(): Int =
        ServerSocket(0).use { socket -> socket.localPort }

    private fun launchArguments(identity: String, port: Int): Array<String> = arrayOf(
        "--spring.application.name=redis-dead-endpoint-test",
        "--spring.main.web-application-type=reactive",
        "--server.port=0",
        "--spring.rsocket.server.port=0",
        "--app.server.proto=rsocket",
        "--app.key.type=long",
        "--app.nodeid=1",
        "--app.service.core.key=memory",
        "--app.service.core.pubsub=redis-pubsub",
        "--app.service.core.index=lucene",
        "--app.service.core.persistence=memory",
        "--app.service.core.secrets=memory",
        "--app.service.composite=true",
        "--app.service.composite.auth=true",
        "--app.service.core.vector=redis",
        "--app.service.core.embedding=openai",
        "--app.service.core.embedding.identity=$identity",
        "--app.service.core.embedding.openai.base-url=http://127.0.0.1:$port",
        "--app.service.core.embedding.openai.api-key=not-a-secret",
        "--app.service.core.embedding.openai.model=stub-embedding",
        "--app.controller.message=true",
        "--app.controller.recall=true",
        "--spring.redis.host=${redisStack.host}",
        "--spring.redis.port=${redisStack.firstMappedPort}",
        "--redis-topics.host=${redisStack.host}",
        "--redis-topics.port=${redisStack.firstMappedPort}",
        "--spring.cloud.consul.enabled=false",
        "--spring.cloud.consul.discovery.enabled=false",
        "--spring.cloud.consul.config.enabled=false",
    )

    @Test
    fun `an unreachable endpoint fails startup under redis when the index is new`() {
        val identity = "dead-" + UUID.randomUUID().toString().take(8)
        val port = closedPort()

        val thrown = catchThrowable {
            SpringApplicationBuilder(BootApp::class.java)
                .run(*launchArguments(identity, port))
                .close()
        }

        // A bare isNotNull would pass on a missing bean or a bad property
        // name. Each of those is a defect in the test, not the behaviour under
        // test. So this check reads the whole cause chain.
        assertThat(thrown).describedAs("expected a failure").isNotNull()

        val chain = generateSequence(thrown) { it.cause }.toList()
        val text = chain.joinToString(" | ") { "${it.javaClass.name}: ${it.message}" }

        assertThat(chain.map { it.javaClass.name })
            .describedAs("the failure must not be a missing bean: %s", text)
            .doesNotContain("org.springframework.beans.factory.NoSuchBeanDefinitionException")

        // The type, and not only the text. A port number can appear in a
        // message that a live server returned, so the text check alone would
        // pass on an HTTP 500. A refused TCP connection is always a
        // ConnectException. Netty's AnnotatedConnectException extends it, so
        // one check covers every client this repository builds.
        assertThat(chain.any { it is ConnectException })
            .describedAs("the failure must be a refused connection: %s", text)
            .isTrue()

        assertThat(text)
            .describedAs("the failure must name the closed endpoint")
            .contains(port.toString())
    }
}
```

The launch values above come from `RedisVectorRecallBootTests`. Four values
differ, and each difference is deliberate. The vector selector stays `redis`.
The embedding selector becomes `openai`. The identity is fresh for each run, so
the index is always absent. The base URL names the closed port.

`docs/NODEID-CLAIM.md` needs no new row. This test claims no node id, for the
same reason that `RedisVectorRecallBootTests` claims none.

- [ ] **Step 4: Run the redis proof**

```bash
mvn -o -pl chat-core,chat-deploy-redis -Pintegration -Dtest=RedisDeadEmbeddingEndpointTests -Dsurefire.failIfNoSpecifiedTests=false clean verify
```

Expected: PASS, 1 test. This run needs Docker.

- [ ] **Step 5: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/DeadEmbeddingEndpointTests.kt \
        chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisDeadEmbeddingEndpointTests.kt
git commit -F - <<'MSG'
test: prove each failure moment of an unreachable endpoint (CHAT-etfnihnu)

An unreachable embedding endpoint fails at a different moment for each
vector store. EmbeddingModel.dimensions() is the reason, and each store
asks at a different time.

embedded fails at startup always, because the collection bean takes its
width from that call. redis fails at startup only when the index is new,
because schema initialization skips the call when the index exists. simple
never asks at build time, so it fails at the first vector operation.

Each test points at a closed loopback port, so none needs an external
network or a secret key. Each one reads the whole cause chain, requires a
java.net.ConnectException in it, requires the closed port number in the
message text, and refuses a NoSuchBeanDefinitionException. A bare check for
any exception would pass on a missing bean or a held port.

Evidence, measured on <DATE>.

- 2 tests pass in DeadEmbeddingEndpointTests.
- 1 test passes in RedisDeadEmbeddingEndpointTests, under -Pintegration.
- The simple case refreshed its context and failed at the first add, which
  is what the design document states.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 9: The packaged launch gate

**Prerequisite:** `CHAT-jdsamcia` must be done. The gate calls actuator routes
and application routes, and it needs each chain to own its own routes.

**Files:**
- Create: `shell-scripts/vector/openai-stub-server.py`
- Create: `shell-scripts/vector/gate-embedding-launch.sh`

**Interfaces:**
- Consumes: every earlier task.
- Produces: `./shell-scripts/vector/gate-embedding-launch.sh`. Exit status 0
  means the packaged deployment embedded, rebuilt, and searched.

A test classpath cannot prove this feature works. Every test gate in this
repository puts test outputs on the classpath, which is what hid the defect. So
this gate runs outside a test classpath.

- [ ] **Step 1: Confirm the prerequisite is done**

```bash
FP_AGENT_NAME='sigma' fp issue show CHAT-jdsamcia | head -5
```

Expected: `Status: done`.

Stop this task if the status is not done. Without it two filter chains match
every exchange, and the winner depends on bean ordering.

- [ ] **Step 2: Write the stub endpoint**

Create `shell-scripts/vector/openai-stub-server.py`.

```python
#!/usr/bin/env python3
"""A synthetic OpenAI embeddings endpoint.

The gate runs production client code against this process. OpenAiEmbeddingModel
builds, serializes a request, reads a response, and returns vectors. So the
client path is real, and only the service is synthetic.

The provider exists to serve any OpenAI-compatible endpoint, so a compatible
endpoint is a valid subject.

The vectors are deterministic character bigrams. Two texts that share
substrings score higher than two that do not. That is enough for one search to
return a ranked answer.

The process ignores the Authorization header. The gate needs no secret key.
Project policy requires a key value, so the launch supplies a dummy.
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

DIMENSIONS = 256


def bigram_vector(text):
    vector = [0.0] * DIMENSIONS
    padded = " " + text.lower() + " "
    for i in range(len(padded) - 1):
        slot = (ord(padded[i]) * 128 + ord(padded[i + 1])) % DIMENSIONS
        vector[slot] += 1.0
    norm = sum(v * v for v in vector) ** 0.5
    if norm > 0:
        vector = [v / norm for v in vector]
    return vector


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")

        given = body.get("input", [])
        texts = [given] if isinstance(given, str) else list(given)

        payload = {
            "object": "list",
            "model": body.get("model", "stub-embedding"),
            "data": [
                {"object": "embedding", "index": i, "embedding": bigram_vector(t)}
                for i, t in enumerate(texts)
            ],
            "usage": {"prompt_tokens": 0, "total_tokens": 0},
        }

        encoded = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt, *args):
        sys.stderr.write("stub: " + (fmt % args) + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
```

- [ ] **Step 3: Confirm the stub answers**

```bash
source ~/miniforge3/etc/profile.d/conda.sh && conda activate base
python3 shell-scripts/vector/openai-stub-server.py 9099 &
STUB=$!
sleep 1
curl -sS -X POST http://127.0.0.1:9099/v1/embeddings \
  -H 'Content-Type: application/json' \
  -d '{"model":"stub","input":["apple pie recipe"]}' | python3 -c "
import json, sys
body = json.load(sys.stdin)
print('vectors', len(body['data']))
print('width', len(body['data'][0]['embedding']))
"
kill "$STUB"
```

Expected: `vectors 1` and `width 256`.

- [ ] **Step 4: Write the gate script**

Create `shell-scripts/vector/gate-embedding-launch.sh`.

```bash
#!/bin/bash
#
# Proves the embedding feature outside a test classpath.
#
# A test classpath cannot prove this feature works. Every test gate in this
# repository puts test outputs on the classpath, which is what hid the original
# defect. See CHAT-etfnihnu.
#
# The gate builds a packaged deployment, asserts that no test output is on its
# classpath, launches it against a synthetic OpenAI endpoint, seeds messages
# through persistence, rebuilds the index, and runs one search.
#
# Production client code runs against a synthetic endpoint. The gate needs no
# secret key and no external network.
#
#   ./shell-scripts/vector/gate-embedding-launch.sh
#
# Exit status: 0 when every assertion holds, 1 when one fails.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/../.." && pwd )"
cd "$ROOT" || exit 1

IDENTITY="gate-stub-v1"
STUB_PORT=9099
APP_PORT=8080
ACTUATOR_USER="actuator"
ACTUATOR_PASS="actuator"
WORK=$(mktemp -d)
STUB_PID=""
APP_PID=""

cleanup() {
    [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null
    [ -n "$STUB_PID" ] && kill "$STUB_PID" 2>/dev/null
    rm -rf "$WORK"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $1"
    exit 1
}

# CLAUDE.md requires miniforge for Python. The stub runs under it, and so does
# every JSON read below. The activation happens once, here, and it must
# succeed. A later step that silently used a system python3 would break the one
# rule this repository sets.
# shellcheck disable=SC1090
if [ -f "$HOME/miniforge3/etc/profile.d/conda.sh" ]; then
    source "$HOME/miniforge3/etc/profile.d/conda.sh"
    conda activate base || fail "conda activate base failed"
else
    fail "miniforge is not installed at ~/miniforge3. See CLAUDE.md."
fi
command -v python3 > /dev/null || fail "no python3 after conda activate base"

echo "0. Python runs under miniforge."
python3 -c "import sys; print('   ' + sys.executable)"

echo "1. Build and package chat-deploy-memory with expose-webflux."
mvn -o -B -Pexpose-webflux -Dmaven.test.skip=true \
    -pl chat-deploy-memory -am \
    clean package > "$WORK/build.log" 2>&1 \
    || { tail -40 "$WORK/build.log"; fail "the build did not finish"; }

JAR=$(find chat-deploy-memory/target -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' ! -name '*.original' | head -1)
[ -n "$JAR" ] || fail "no packaged jar in chat-deploy-memory/target"
echo "   jar: $JAR"

echo "2. Assert that no test output is on the launched classpath."
TESTS=$(unzip -l "$JAR" | grep -c 'tests\.jar')
[ "$TESTS" -eq 0 ] || { unzip -l "$JAR" | grep 'tests\.jar'; fail "a test jar is inside the packaged artifact"; }
echo "   ok, no test jar inside the artifact"

echo "3. Start the synthetic embeddings endpoint."
python3 "$DIR/openai-stub-server.py" "$STUB_PORT" > "$WORK/stub.log" 2>&1 &
STUB_PID=$!
sleep 1
kill -0 "$STUB_PID" 2>/dev/null || fail "the stub did not start"

echo "4. Launch the packaged deployment."
java --enable-native-access=ALL-UNNAMED -jar "$JAR" \
    --app.nodeid=1 \
    --app.key.type=long \
    --spring.application.name=gate-embedding \
    --app.server.proto=rest \
    --server.port="$APP_PORT" \
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
    --app.service.core.embedding.identity="$IDENTITY" \
    --app.service.core.embedding.openai.base-url="http://127.0.0.1:$STUB_PORT" \
    --app.service.core.embedding.openai.api-key=not-a-secret \
    --app.service.core.embedding.openai.model=stub-embedding \
    --app.controller.persistence=true \
    --app.controller.recall=true \
    --app.controller.message=true \
    --app.controller.topic=true \
    --app.controller.user=true \
    --app.controller.key=true \
    --app.controller.index=true \
    --app.actuator.username="$ACTUATOR_USER" \
    --app.actuator.password="$ACTUATOR_PASS" \
    --management.endpoint.vectorindex.enabled=true \
    --management.endpoints.web.exposure.include=vectorindex,health \
    > "$WORK/app.log" 2>&1 &
APP_PID=$!

echo "   waiting for health"
for _ in $(seq 1 60); do
    curl -sf "http://127.0.0.1:$APP_PORT/actuator/health" > /dev/null 2>&1 && break
    kill -0 "$APP_PID" 2>/dev/null || { tail -40 "$WORK/app.log"; fail "the deployment exited"; }
    sleep 2
done
curl -sf "http://127.0.0.1:$APP_PORT/actuator/health" > /dev/null \
    || { tail -40 "$WORK/app.log"; fail "the deployment did not answer health"; }
echo "   ok, the deployment is up"

echo "5. Assert that an actuator call without credentials answers 401."
CODE=$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:$APP_PORT/actuator/vectorindex")
[ "$CODE" = "401" ] || fail "an actuator call without credentials answered $CODE, expected 401"
echo "   ok, 401"

echo "6. Seed three messages through persistence, with no credentials."
# MessageSendRequest carries msg, from, and dest. RequestResponse declares
# @JsonTypeInfo as a property named type, and MessageSendRequest declares
# @JsonTypeName, so the body must carry that discriminator. The route declares
# @ResponseStatus(HttpStatus.CREATED), so it answers 201.
for text in "apple pie recipe" "banana bread recipe" "carrot soup recipe"; do
    CODE=$(curl -sS -o /dev/null -w '%{http_code}' \
        -X PUT "http://127.0.0.1:$APP_PORT/persist/message/add" \
        -H 'Content-Type: application/json' \
        -d "{\"type\":\"MessageSendRequest\",\"msg\":\"$text\",\"from\":10,\"dest\":20}")
    [ "$CODE" = "201" ] || fail "the seed answered $CODE for '$text', expected 201"
done
echo "   ok, three messages persisted"

echo "7. Trigger a rebuild, under Basic credentials."
curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" \
    -X POST "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > "$WORK/trigger.json" \
    || fail "the trigger did not answer"

echo "8. Poll until running is false."
for _ in $(seq 1 60); do
    curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" \
        "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > "$WORK/status.json"
    RUNNING=$(python3 -c "
import json
print(json.load(open('$WORK/status.json'))['status']['running'])
" 2>/dev/null)
    [ "$RUNNING" = "False" ] && break
    sleep 2
done
[ "$RUNNING" = "False" ] || { cat "$WORK/status.json"; fail "the rebuild did not finish"; }
echo "   ok, the rebuild finished"

echo "9. Assert that the newest job carries the identity."
JOB_IDENTITY=$(python3 -c "
import json
jobs = json.load(open('$WORK/status.json'))['jobs']
jobs.sort(key=lambda j: j['startedAt'], reverse=True)
print(jobs[0].get('embeddingIdentity'))
")
[ "$JOB_IDENTITY" = "$IDENTITY" ] \
    || fail "the newest job carries identity '$JOB_IDENTITY', expected '$IDENTITY'"
echo "   ok, the job carries $IDENTITY"

echo "10. Run one recall, with no credentials."
curl -sS -X POST "http://127.0.0.1:$APP_PORT/message/recall/topic" \
    -H 'Content-Type: application/json' \
    -d '{"type":"TopicRecallRequest","topicId":20,"query":"recipe","limit":10}' \
    > "$WORK/recall.json" || fail "the recall did not answer"

python3 -c "
import json, sys
body = json.load(open('$WORK/recall.json'))
if body.get('indexComplete') is not True:
    print('indexComplete is', body.get('indexComplete'), 'expected True')
    sys.exit(1)
hits = body.get('hits', [])
if len(hits) < 3:
    print('hits', len(hits), 'expected at least 3')
    sys.exit(1)
print('   ok,', len(hits), 'hits and indexComplete true')
" || { cat "$WORK/recall.json"; fail "the recall answer is wrong"; }

echo
echo "PASS. The packaged deployment embedded, rebuilt, and searched."
```

- [ ] **Step 5: Make the scripts runnable**

```bash
chmod +x shell-scripts/vector/gate-embedding-launch.sh shell-scripts/vector/openai-stub-server.py
```

- [ ] **Step 6: Run the gate**

```bash
./shell-scripts/vector/gate-embedding-launch.sh
```

Expected: exit status 0, and the last line reads `PASS`.

**This run needs no external network and no secret key.** The stub answers on
loopback HTTP, so the run does use a network socket. Project policy requires a
key value, and the launch supplies a dummy that is not a secret.

The script writes each failure to a file under its own working directory, and
it prints the last 40 lines of the build log or the application log before it
exits. Read that output. The script does not continue after a failed step.

- [ ] **Step 7: Prove the gate catches a missing provider**

Change the launch to `--app.service.core.embedding=mock` and remove the
identity argument. Run the gate.

Expected: exit status 1. The deployment fails to start, because no bean
supplies an `EmbeddingModel` from a main source.

This is the exact defect the issue records. Restore the arguments. Run the gate
again and confirm it passes.

- [ ] **Step 8: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add shell-scripts/vector/gate-embedding-launch.sh shell-scripts/vector/openai-stub-server.py
git commit -F - <<'MSG'
test: prove the embedding feature outside a test classpath (CHAT-etfnihnu)

The gate proves this feature outside a test classpath. Every test gate in
this repository puts test outputs on the classpath, which is what hid the
original defect.

The gate builds a packaged deployment, asserts that no test output is
inside the artifact, launches it against a synthetic OpenAI endpoint, seeds
three messages through persistence, rebuilds the index, and runs one
search. Production client code runs against a synthetic endpoint, so the
gate needs no external network and no secret key.

Evidence, measured on <DATE>.

- The gate exits 0.
- The packaged artifact holds no file whose name ends tests.jar.
- An actuator call without credentials answered 401.
- Three seeds through PUT /persist/message/add each answered 201.
- The newest job carried the identity that the launch set.
- One recall returned <N> hits with indexComplete true.
- Mutation: a launch with embedding=mock and no identity failed to start.
  That is the defect CHAT-etfnihnu records.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 10: The operator document and the register

**Files:**
- Create: `docs/EMBEDDING-PROVIDERS.md`
- Modify: `docs/BUILD-HEALTH.md`
- Modify: `forward-register.md`

**Interfaces:**
- Consumes: every earlier task.
- Produces: no code.

- [ ] **Step 1: Measure the current build**

```bash
LOG=$(mktemp -d)

# Each run sits inside an if. A bare call followed by $? would abort the shell
# when errexit is inherited, and the status would never be read.
if ./shell-scripts/build-health.sh > "$LOG/default.txt" 2>&1; then
    echo "default exit: 0"
else
    echo "default exit: $?"
fi
if ./shell-scripts/build-health.sh --integration > "$LOG/integration.txt" 2>&1; then
    echo "integration exit: 0"
else
    echo "integration exit: $?"
fi

tail -20 "$LOG/default.txt"
tail -20 "$LOG/integration.txt"
echo "logs: $LOG"
```

Expected: both exit lines read `0`.

Never pipe the gate into `tail`. The pipeline reports the exit status of
`tail`, so a failed build would read as a pass.

Write down the module count, the test count, the failure count, and the skip
count from each mode. Use the measured numbers in Step 3. Do not copy the
numbers from an earlier session.

- [ ] **Step 2: Write the operator document**

Create `docs/EMBEDDING-PROVIDERS.md`. Cover these sections.

1. **The three embedding values.** `mock` is test only and lives in the
   `chat-core` test jar. `openai` reaches an OpenAI-compatible endpoint.
   `local` loads an ONNX model in the process.
2. **The ten legal selector pairs.** Copy the table from the spec. Say that a
   mock vector store refuses a production model.
3. **The identity.** State the property, the character rule, and the three
   failures. State that a new identity means a new corpus and not a migration.
   State that the operator names it, because only an operator knows that a
   remote service changed its model.
4. **Each provider's properties.** Three for `openai`, and three for `local`
   including the cache path and its default.
5. **What orphans.** An existing redis index and an existing embedded directory
   both orphan, because their names carry no identity segment. An existing job
   record carries null and never covers, so the first start reports an
   incomplete index.
6. **Failure behavior.** Copy the table from the spec. `embedded` fails at
   startup always. `redis` fails at startup only when the index is new.
   `simple` fails at the first vector operation.
7. **Manual procedure one: a real OpenAI endpoint.** Give the exact launch
   arguments. State that this needs a real key and network access. State that
   this procedure does not run unattended.
8. **Manual procedure two: a real ONNX model.** Give the exact launch
   arguments. Name a model and a tokenizer that an operator can download. State
   the file sizes. State that this repository carries neither file.
9. **The guard.** Name `just check-production-classpath` and both rules. State
   why two rules exist.

- [ ] **Step 3: Update the build health record**

Modify `docs/BUILD-HEALTH.md`. Change the module count to the measured value.
Change both test counts to the measured values. Add one row that names the two
new modules.

- [ ] **Step 4: Update the forward register**

Modify `forward-register.md`. Add one section for this work. Cover these facts.

- The defect: a test jar carried the only `EmbeddingModel` onto a production
  classpath, and no test could detect it.
- The two new modules and their selector values.
- The identity, and that the operator names it.
- That a legacy job never covers, so the first start rebuilds.
- That the guard holds two rules, and why one rule cannot replace the other.
- That the gate runs outside a test classpath, and why every test gate could
  not.
- Any trap this work found that costs a debugging cycle.

Measure every field of every row that you touch. Do not correct only the field
that a review names.

- [ ] **Step 5: Run every gate once more**

```bash
LOG=$(mktemp -d)
STATUS=0

run_gate() {
    # Each argument stays a separate word. A loop over command strings would
    # break under zsh, which is this repository's shell. Zsh does not word
    # split an unquoted scalar, so "build-health.sh --integration" would read
    # as one executable name.
    name="$1"; shift
    # The command runs inside an if. A bare call followed by code=$? would
    # abort the shell when errexit is inherited, and the status would never be
    # read. An if condition is exempt from errexit.
    if "$@" > "$LOG/$name.txt" 2>&1; then
        code=0
    else
        code=$?
        STATUS=1
    fi
    echo "$code  $name"
}

run_gate default      ./shell-scripts/build-health.sh
run_gate integration  ./shell-scripts/build-health.sh --integration
run_gate classpath    ./shell-scripts/check-production-classpath.sh
run_gate versions     ./shell-scripts/check-dependency-versions.sh
run_gate launch       ./shell-scripts/vector/gate-embedding-launch.sh

echo "logs: $LOG"
echo "overall: $STATUS"
[ "$STATUS" -eq 0 ] || { echo "AT LEAST ONE GATE FAILED. Do not commit."; false; }
```

Expected: five lines that each begin with `0`, then `overall: 0`. The last
line prints nothing.

The final test is what makes this step fail. Without it the block ends on an
`echo`, which always succeeds, and a failed gate would read as a pass.

Each gate writes to its own file, and the loop reads each exit status before it
continues. A pipeline into `tail` would report the status of `tail` and hide a
failed build or a failed launch.

Read the matching file when a line does not begin with `0`. The numbers in the
first two files must match what Step 3 records.

- [ ] **Step 6: Commit**

Replace each angle bracket in the body below with a measured value, and
delete a line rather than guess at it. Then run the block.

```bash
drift check && git diff --check
git add docs/EMBEDDING-PROVIDERS.md docs/BUILD-HEALTH.md forward-register.md
git commit -F - <<'MSG'
docs: record the embedding providers and the guard (CHAT-etfnihnu)

docs/EMBEDDING-PROVIDERS.md is the operator document for this feature. It
names the three embedding values, the ten legal selector pairs, the
identity rule, the properties of each provider, what orphans, and the
failure moment of each vector store.

It also carries the two procedures that stay manual. A run against a real
OpenAI endpoint needs a key. A run of chat-embedding-local needs an ONNX
model and a tokenizer, which this repository does not carry. Each one runs
only when an operator asks for it.

forward-register.md records the defect and the lesson. A test jar carried
the only EmbeddingModel in this repository onto a production classpath, and
no test could detect it, because a Spring Boot test puts test jars on its
classpath by construction. That is why the gate runs outside a test
classpath and why the guard reads a resolved runtime classpath.

Evidence, measured on <DATE>.

- Default build: <N> tests, <N> failures, <N> errors, <N> skipped.
- Integration build: <N> tests, <N> failures, <N> errors, <N> skipped.
- The reactor reports <N> modules.
- check-production-classpath.sh exits 0.
- check-dependency-versions.sh exits 0.
- gate-embedding-launch.sh exits 0.
- docs/BUILD-HEALTH.md carries these numbers and no other.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 7: Close the issue**

Write the closing comment into a file first. A heredoc keeps the blank lines
and the list shape, and `fp comment` takes the file through a command
substitution.

```bash
FP_AGENT_NAME='sigma' fp issue assign CHAT-etfnihnu --rev $(git rev-parse --short=8 HEAD)

NOTE=$(mktemp)
cat > "$NOTE" <<'EOF'
<the filled template below>
EOF
FP_AGENT_NAME='sigma' fp comment CHAT-etfnihnu "$(cat "$NOTE")"
```

Fill the template below before you run those commands. Replace each angle
bracket with a value that Step 5 measured. Delete any line this session did not
measure, rather than guess at it.

```
The work is complete and it waits for review.

What landed. Two production embedding modules, one selector value each.
chat-embedding-openai reaches an OpenAI compatible endpoint.
chat-embedding-local loads an ONNX model in the process. The mock stays
test only, in the chat-core test jar.

The defect is closed. chat-deploy-memory declared the chat-core test jar
with no scope element, so the only EmbeddingModel in this repository
resolved at compile and reached a launch. That dependency now declares test
scope, and both deployments declare both production modules.

Every production model carries an identity that the operator names. The
identity reaches the redis index name, the embedded collection directory,
the local resource cache directory, every new IndexJob, and the coverage
filter.

Evidence, measured on <DATE>.

- Default build: <N> tests, <N> failures, <N> errors, <N> skipped.
- Integration build: <N> tests, <N> failures, <N> errors, <N> skipped.
- The reactor reports <N> modules.
- check-production-classpath.sh exits 0.
- check-dependency-versions.sh exits 0.
- gate-embedding-launch.sh exits 0.

Mutation proofs, and each result.

- The coverage identity filter moved after next(): the foreign newer job
  test failed.
- A hardcoded embedded storage directory: the wiring test failed and the
  companion tests passed.
- A literal redis index name: the redis wiring test failed and the name
  tests passed.
- A hardcoded local cache directory: the remote cache test failed and the
  two file tests passed.
- Guard rule one, with the memory test jar scope removed: rule one failed.
- Guard rule two, with spring-boot-starter-test scope removed: rule two
  failed and rule one passed.
- The packaged gate with embedding=mock and no identity: the launch failed
  to start.

What is not proven here. <List anything a step could not measure, or write
'Nothing. Every step in the plan ran.'>
```

Leave the status at `in-progress`. The owner closes the issue after review.

---

## Self-Review

I checked the plan against the spec.

**Spec coverage.** Each verification line of the spec maps to a task.

| Spec verification line | Task |
|---|---|
| Each provider supplies a model behind its value | 2, 3 |
| The local provider loads a real model and embeds text | 3 |
| Each provider supplies no bean for another value | 2, 3 |
| Neither module depends on a starter | 2, 3, by dependency tree |
| A production embedding without an identity fails | 1 |
| A mock embedding with an identity fails | 1 |
| An illegal character fails | 1 |
| A mock embedding resolves `mock` | 1 |
| No vector selector resolves no identity and still starts | 1 |
| The redis name and prefix carry the identity | 4 |
| The embedded directory carries the identity | 4 |
| The local cache directory carries the identity | 3 |
| A successful job records the identity | 5 |
| A job of another identity does not cover | 5 |
| A legacy job does not cover | 5 |
| A newer foreign job does not hide a covering job | 5 |
| The release sweep releases a foreign stale job | 5, by no change |
| The actuator read returns a foreign job | 5, by no change |
| An unreachable endpoint fails startup under `embedded` | 8 |
| An unreachable endpoint fails startup under `redis` when new | 8 |
| An unreachable endpoint fails the first operation under `simple` | 8 |
| A packaged launch has no test output | 9 |
| The gate seeds through persistence | 9 |
| Each actuator call carries credentials, and 401 without | 9 |
| The deployment embeds through the production client | 9 |
| The job carries the identity | 9 |
| The deployment completes one search | 9 |

Every verification line now maps to an automated check. The first revision left
the three failure-moment lines as manual procedures. Task 8 replaces them, at
the owner's direction.

**What the tasks prove beyond the spec's own list.**

- Task 3 Step 13 loads a real ONNX model through the configuration, asserts 384
  dimensions, and asserts that two texts which share meaning score above two
  that do not. One further test uses `https:` resources, because
  `ResourceCacheService` does not copy a `file:` resource and only a remote one
  can show that the bean passed the right cache directory. That test builds two
  identities, so it also shows that a new identity reads no old bytes.
- Task 3 Step 12 runs those tests against a broken production path first. The
  configuration already exists by then, so a red step must break the
  implementation rather than omit it.
- Task 4 Step 14 proves that each vector store bean calls the identity function
  rather than a hardcoded name. The companion tests alone cannot show that.
- Task 5 Step 12 proves that a record written before this change decodes with a
  null identity, in both the JSON string shape and the map shape. The coverage
  filter rests on that decode.
- Task 7 Steps 5 and 6 prove that each guard rule catches a breach that the
  other rule cannot see.

**Two items that need no change, and why.**

1. **The release sweep and the actuator read.** The spec says neither filters on
   identity. Task 5 changes neither, so no new test covers them. The existing
   tests already pin their behaviour, and the identity field does not enter
   either path.
2. **Six existing test classes.** Task 4 Step 12 and Task 5 Step 15 each name
   the exact edit, and each names the classes that need none. `ChatApp` declares
   `scanBasePackages = ["com.demo.chat.config"]`, and every boot test sets
   `app.service.core.embedding=mock`, which resolves the fixed identity with no
   property.

**One step the spec does not name.** `chat-vector-embedded` declares `chat-core`
only as a test jar. Task 4 Step 5 adds the compile scope dependency. The module
cannot read `EmbeddingIdentity` without it.

**Two unverified library surfaces.** Neither `spring-ai-openai` nor
`spring-ai-transformers` was on this machine when I wrote this plan. The
constructor and setter names in Task 2 and Task 3 are expected shapes and not
measured ones. Each task carries a step that reads the real signatures from the
downloaded jar before the configuration is written.

**Two library surfaces that a step measures rather than assumes.** Task 2 and
Task 3 each read the real signatures from the downloaded jar.

**The model is pinned, and both checksums were measured.** The revision is
`1110a243fdf4706b3f48f1d95db1a4f5529b4d41`. `onnx/model.onnx` is 90405214 bytes
and `tokenizer.json` is 466247 bytes. I fetched both on 2026-09-14 and computed
each sha256, and the model checksum also matches the one the host publishes. A
`main` URL would serve whatever that branch holds today, and a mutable URL under
a fixed identity would let two models share one name. That is the exact failure
the identity exists to prevent.

**No task repairs a breach that another issue owns.** Task 7 stops and reports
when either guard rule fails, because `CHAT-xvtsffqh` owns every scope change
that a rule can report. It carries the exact `fp comment` command for that
report.

**No task carries a conditional pom edit.** Task 8 needs
`--add-modules jdk.incubator.vector` on the surefire JVM of
`chat-deploy-memory`, and `chat-deploy-memory/pom.xml:124` already declares it.
So that task changes no pom, and its file list says so.

**Every `fp` command carries `FP_AGENT_NAME='sigma'`.** This repository sets
that convention, at line 29 of
`docs/superpowers/plans/2026-09-10-vector-reindex.md`. The two prerequisite
checks, the Task 7 report, and both closing commands each carry it.

**Every commit body and the closing comment carry a template.** Each template
uses angle brackets for a value that only the run can supply, and each says to
delete a line rather than guess at it.

**One unverified spec statement that Task 8 can overturn.** The spec says
`SimpleVectorStore` never calls `dimensions()` at build time. Task 8 Step 3
says what to do when that proves false. Record a spec correction and tell the
owner. Do not change the test to match.

**Every assertion names what it rejects.** No test in this plan asserts only
that something was thrown. Task 8 reads the whole cause chain, requires a
`java.net.ConnectException` in it, requires the closed port number in the
message text, and refuses a `NoSuchBeanDefinitionException`. A missing bean, a
misspelled property, a port that another process holds, and an HTTP error from
a live server each fail the test rather than passing it.

**The manual requirement is honored by a mechanism, not by prose.** The spec
says that a run of `chat-embedding-local` never runs unattended.
`LocalEmbeddingModelTests` carries the integration tag, and every test in it
needs `-Dchat.embedding.local.manual=true`. The remote test needs a second
property as well. Task 3 Step 13 runs the module a second time with neither
property, with the downloaded files still in place, and requires all three to
report as skipped.

**The download cost is stated and bounded.** One file set is 90871461 bytes,
which is 86.7 MiB. The remote test takes two sets, one per identity, and it
runs twice across the task, which is four sets. With the one set that Step 10
takes, the task
downloads five sets, which is 433.3 MiB.

Two changes cut that figure, and each cuts a different amount. Merging the two
remote tests into one removed **one set from every remote run**, because the
earlier pair cost three sets and the single test costs two. Running Step 12's
second mutation without the remote property removed **one whole run of two
sets**, because that mutation breaks the tokenizer resource and no remote test
reads it.

The first revision of this figure also miscounted the remaining runs as four
sets rather than five. The table in Step 10 now carries one row per run, so the
sum is visible rather than derived in prose.

A gate on the downloaded files alone would not hold the rule. A developer who
ran the download once would load a 90 MiB model in every later integration
build on that machine. So the switch is a property and not a file check.

**All ten commits carry a body, and every template sits inside its own
heredoc.** A template printed
beside the command would leave a subject only message, because the command
would already carry the whole message. Each `git commit -F - <<'MSG'` block now
holds the subject, the body, and the trailer, and the instruction to replace
each angle bracket sits above the block rather than inside it.

**Every fixture is derived, not hand written.** Task 5 Step 12 builds the
legacy record by removing one field from the mapper's own output. A hand
written JSON literal could disagree with the real wire shape, because `Key`
carries a wrapper object and the path to an id is one level deeper than its
field name.

**Every commit carries the trailer.** All ten commits use a `git commit -F -`
heredoc, so each message ends with the required `Co-Authored-By` line. A
`git commit -m` one liner cannot carry it.

**Every script that runs Python enforces miniforge.** The guard script and the
gate script each activate it once, near the top, and each exits when the
activation fails. A later step cannot fall back to a system interpreter in
silence.

**Every gate reports its own exit status.** No step pipes a build or a launch
into `tail`. Task 10 Step 5 ends on a test rather than an `echo`, so a failed
gate fails the step.

**Placeholder scan.** The plan carries no `TBD` and no `implement later`. Each
code step carries the code, including both dead endpoint test classes in full.
Task 10 Step 2 lists nine sections rather than the prose, because an operator
document is prose that the writer must measure.

**Type consistency.** `EmbeddingIdentity` carries the same name in every task.
`embeddingIdentity` is the constructor argument name in Task 5, the bean name
in every registration, and the field name on `IndexJob`. `indexNameFor`,
`prefixFor`, `storageDirectoryFor`, and `cacheDirectoryFor` each appear as one
definition and as calls that match it.
