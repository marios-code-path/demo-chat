# Capability Mechanism Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the capability matrix data — declared on providers, resolved before the context starts, and enforced — without deleting a single module.

**Architecture:** Two annotations declare what a class provides and requires. A bytecode scan collects them into a registry that includes providers whose conditions did not match. A pure resolver unions the selected compositions, checks cover and provider availability, and produces either a resolution or a list of named problems. A Spring `EnvironmentPostProcessor` runs that resolver before auto-configuration — first reporting only, then enforcing.

**Tech Stack:** Kotlin, Spring Boot 3.3.13, JUnit 5, AssertJ, Maven, SnakeYAML (already on the classpath via `spring-boot-starter`)

**Spec:** `docs/superpowers/specs/2026-08-24-capability-composition-design.md`

## Global Constraints

- Everything in this plan lands in `chat-core` except Task 5 (annotations on providers across backend modules), Task 6 (`compositions.yml` in `chat-deploy`) and Task 7 (`matchIfMissing` removal in `chat-persistence-memory`).
- Package for all new code: `com.demo.chat.capability`.
- Kotlin, matching surrounding style: no semicolons, expression bodies where natural, explicit types on public API.
- No module is deleted, no image changes, no selector is renamed. Those are spec migration steps 5-7 and belong to a second plan.
- The tree must be releasable after every task: `./shell-scripts/build-health.sh` exits 0.
- Maven runs offline against a stale local repo unless upstream modules are in the same reactor. Always run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone — a single-module run resolves `chat-core` from `~/.m2` and reports failures that are not real.
- Commit after every task.

## Scope

Implements spec migration steps 1-4. Explicitly **not** in this plan, and not a coverage gap:

- `spring.autoconfigure.exclude` computation (spec Resolution step 5) — nothing to exclude until the classpath is merged in step 5.
- Service naming, consul tags, unique instance-id, discovery, partition, store stamp — spec steps 6-7.

## File Structure

| File | Responsibility |
|------|----------------|
| `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityAnnotations.kt` | The two annotations. Nothing else. |
| `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityRegistry.kt` | Descriptor types and lookups over them |
| `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityScanner.kt` | Bytecode scan producing a registry |
| `chat-core/src/main/kotlin/com/demo/chat/capability/CompositionCatalog.kt` | Loads and holds `compositions.yml` |
| `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityResolver.kt` | Pure resolution: registry + catalog + request in, resolution or problems out |
| `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityEnvironmentPostProcessor.kt` | Spring glue: reads the Environment, runs the resolver, reports or throws |
| `chat-core/src/main/resources/META-INF/spring.factories` | Registers the post-processor |
| `chat-deploy/src/main/resources/compositions.yml` | The blessed compositions |

---

### Task 1: Annotations and the scanner

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityAnnotations.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityRegistry.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityScanner.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityScannerTests.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/capability/fixture/Fixtures.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `annotation class ProvidesCapability(val capability: String, val value: String)`
  - `annotation class RequiresCapability(vararg val capabilities: String)`
  - `data class ConditionDescriptor(val property: String, val havingValue: String, val matchIfMissing: Boolean)`
  - `data class ProviderDescriptor(val capability: String, val value: String, val className: String, val condition: ConditionDescriptor?)`
  - `data class ConsumerDescriptor(val capabilities: List<String>, val className: String, val condition: ConditionDescriptor?)`
  - `class CapabilityRegistry(val providers: List<ProviderDescriptor>, val consumers: List<ConsumerDescriptor>)` with `fun valuesFor(capability: String): List<String>` and `fun providerFor(capability: String, value: String): ProviderDescriptor?`
  - `object CapabilityScanner { fun scan(basePackages: List<String>): CapabilityRegistry }`

- [ ] **Step 1: Write the fixtures the test scans**

`chat-core/src/test/kotlin/com/demo/chat/test/capability/fixture/Fixtures.kt`:

```kotlin
package com.demo.chat.test.capability.fixture

import com.demo.chat.capability.ProvidesCapability
import com.demo.chat.capability.RequiresCapability
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@ProvidesCapability(capability = "persistence", value = "fixture-a")
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "fixture-a")
class FixtureProviderA

@ProvidesCapability(capability = "persistence", value = "fixture-b")
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "fixture-b", matchIfMissing = true)
class FixtureProviderB

@ProvidesCapability(capability = "index", value = "fixture-idx")
class FixtureProviderNoCondition

@RequiresCapability("persistence", "index")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class FixtureConsumer
```

- [ ] **Step 2: Write the failing test**

`chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityScannerTests.kt`:

```kotlin
package com.demo.chat.test.capability

import com.demo.chat.capability.CapabilityScanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CapabilityScannerTests {

    private val registry = CapabilityScanner.scan(listOf("com.demo.chat.test.capability.fixture"))

    @Test
    fun `finds every provider regardless of its condition`() {
        assertThat(registry.valuesFor("persistence"))
            .containsExactlyInAnyOrder("fixture-a", "fixture-b")
    }

    @Test
    fun `records the condition beside each provider`() {
        val a = registry.providerFor("persistence", "fixture-a")!!
        assertThat(a.condition!!.property).isEqualTo("app.service.core.persistence")
        assertThat(a.condition!!.havingValue).isEqualTo("fixture-a")
        assertThat(a.condition!!.matchIfMissing).isFalse

        val b = registry.providerFor("persistence", "fixture-b")!!
        assertThat(b.condition!!.matchIfMissing).isTrue
    }

    @Test
    fun `a provider with no condition records none`() {
        assertThat(registry.providerFor("index", "fixture-idx")!!.condition).isNull()
    }

    @Test
    fun `finds consumers and the capabilities they require`() {
        val consumer = registry.consumers.single { it.className.endsWith("FixtureConsumer") }
        assertThat(consumer.capabilities).containsExactly("persistence", "index")
        assertThat(consumer.condition!!.property).isEqualTo("app.controller.persistence")
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -o -pl chat-core test -Dtest=CapabilityScannerTests`
Expected: compilation failure — `Unresolved reference: capability`

- [ ] **Step 4: Write the annotations**

`chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityAnnotations.kt`:

```kotlin
package com.demo.chat.capability

/**
 * Declares that this configuration supplies [capability] when the selector for it
 * holds [value]. Read from bytecode, so a provider is visible to the registry
 * whether or not its condition matched - coverage has to include what did not
 * activate.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProvidesCapability(val capability: String, val value: String)

/**
 * Declares that this configuration cannot function unless every named capability
 * resolves to some provider, local or remote.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresCapability(vararg val capabilities: String)
```

- [ ] **Step 5: Write the registry types**

`chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityRegistry.kt`:

```kotlin
package com.demo.chat.capability

data class ConditionDescriptor(
    val property: String,
    val havingValue: String,
    val matchIfMissing: Boolean
)

data class ProviderDescriptor(
    val capability: String,
    val value: String,
    val className: String,
    val condition: ConditionDescriptor?
)

data class ConsumerDescriptor(
    val capabilities: List<String>,
    val className: String,
    val condition: ConditionDescriptor?
)

class CapabilityRegistry(
    val providers: List<ProviderDescriptor>,
    val consumers: List<ConsumerDescriptor>
) {
    fun valuesFor(capability: String): List<String> =
        providers.filter { it.capability == capability }.map { it.value }.distinct().sorted()

    fun providerFor(capability: String, value: String): ProviderDescriptor? =
        providers.firstOrNull { it.capability == capability && it.value == value }

    fun capabilities(): List<String> = providers.map { it.capability }.distinct().sorted()
}
```

- [ ] **Step 6: Write the scanner**

`chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityScanner.kt`:

```kotlin
package com.demo.chat.capability

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.AnnotationMetadata
import org.springframework.core.type.filter.AnnotationTypeFilter

object CapabilityScanner {

    private const val CONDITIONAL_ON_PROPERTY =
        "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty"

    fun scan(basePackages: List<String>): CapabilityRegistry {
        val providers = mutableListOf<ProviderDescriptor>()
        val consumers = mutableListOf<ConsumerDescriptor>()

        // useDefaultFilters = false, and every candidate is accepted: the default
        // check rejects classes that are not independent or concrete, and a
        // provider is allowed to be either.
        val scanner = object : ClassPathScanningCandidateComponentProvider(false) {
            override fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition): Boolean = true
        }
        scanner.addIncludeFilter(AnnotationTypeFilter(ProvidesCapability::class.java))
        scanner.addIncludeFilter(AnnotationTypeFilter(RequiresCapability::class.java))

        basePackages.forEach { pkg ->
            scanner.findCandidateComponents(pkg).forEach { definition ->
                val metadata = (definition as AnnotatedBeanDefinition).metadata
                val condition = conditionOf(metadata)

                metadata.getAnnotationAttributes(ProvidesCapability::class.java.name)?.let { attrs ->
                    providers += ProviderDescriptor(
                        capability = attrs["capability"] as String,
                        value = attrs["value"] as String,
                        className = metadata.className,
                        condition = condition
                    )
                }

                metadata.getAnnotationAttributes(RequiresCapability::class.java.name)?.let { attrs ->
                    @Suppress("UNCHECKED_CAST")
                    consumers += ConsumerDescriptor(
                        capabilities = (attrs["capabilities"] as Array<String>).toList(),
                        className = metadata.className,
                        condition = condition
                    )
                }
            }
        }
        return CapabilityRegistry(providers, consumers)
    }

    private fun conditionOf(metadata: AnnotationMetadata): ConditionDescriptor? {
        val attrs = metadata.getAnnotationAttributes(CONDITIONAL_ON_PROPERTY) ?: return null

        @Suppress("UNCHECKED_CAST")
        val names = (attrs["name"] as? Array<String>)?.takeIf { it.isNotEmpty() }
            ?: (attrs["value"] as? Array<String>)
            ?: emptyArray()

        val prefix = (attrs["prefix"] as? String).orEmpty().trim('.')
        val property = listOf(prefix, names.firstOrNull().orEmpty())
            .filter { it.isNotEmpty() }
            .joinToString(".")

        return ConditionDescriptor(
            property = property,
            havingValue = (attrs["havingValue"] as? String).orEmpty(),
            matchIfMissing = attrs["matchIfMissing"] as? Boolean ?: false
        )
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -o -pl chat-core test -Dtest=CapabilityScannerTests`
Expected: PASS, 4 tests

- [ ] **Step 8: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/capability chat-core/src/test/kotlin/com/demo/chat/test/capability
git commit -m "feat: declare and scan capability providers"
```

---

### Task 2: Composition catalog

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/capability/CompositionCatalog.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/capability/CompositionCatalogTests.kt`
- Test resource: `chat-core/src/test/resources/test-compositions.yml`

**Interfaces:**
- Consumes: nothing from Task 1
- Produces:
  - `data class Composition(val name: String, val capabilities: Map<String, String>)`
  - `class CompositionCatalog(val compositions: Map<String, Composition>)` with `fun get(name: String): Composition?`
  - `CompositionCatalog.Companion.load(input: InputStream): CompositionCatalog`
  - `CompositionCatalog.Companion.fromClasspath(resource: String): CompositionCatalog` — empty catalog when the resource is absent

- [ ] **Step 1: Write the test resource**

`chat-core/src/test/resources/test-compositions.yml`:

```yaml
compositions:
  memory:      { index: lucene }
  redis:       { key: redis, pubsub: redis-pubsub }
  cassandra:   { persistence: cassandra, secrets: cassandra }
```

- [ ] **Step 2: Write the failing test**

`chat-core/src/test/kotlin/com/demo/chat/test/capability/CompositionCatalogTests.kt`:

```kotlin
package com.demo.chat.test.capability

import com.demo.chat.capability.CompositionCatalog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompositionCatalogTests {

    private val catalog = CompositionCatalog.fromClasspath("test-compositions.yml")

    @Test
    fun `a composition names only the capabilities it serves`() {
        assertThat(catalog.get("redis")!!.capabilities)
            .containsExactlyInAnyOrderEntriesOf(mapOf("key" to "redis", "pubsub" to "redis-pubsub"))
    }

    @Test
    fun `an unknown composition is absent rather than empty`() {
        assertThat(catalog.get("nope")).isNull()
    }

    @Test
    fun `a missing resource yields an empty catalog`() {
        assertThat(CompositionCatalog.fromClasspath("no-such-file.yml").compositions).isEmpty()
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -o -pl chat-core test -Dtest=CompositionCatalogTests`
Expected: compilation failure — `Unresolved reference: CompositionCatalog`

- [ ] **Step 4: Write the catalog**

`chat-core/src/main/kotlin/com/demo/chat/capability/CompositionCatalog.kt`:

```kotlin
package com.demo.chat.capability

import org.yaml.snakeyaml.Yaml
import java.io.InputStream

data class Composition(val name: String, val capabilities: Map<String, String>)

class CompositionCatalog(val compositions: Map<String, Composition>) {

    fun get(name: String): Composition? = compositions[name]

    fun names(): Set<String> = compositions.keys

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun load(input: InputStream): CompositionCatalog {
            val root = Yaml().load<Map<String, Any>>(input) ?: emptyMap()
            val declared = root["compositions"] as? Map<String, Map<String, Any>> ?: emptyMap()
            return CompositionCatalog(
                declared.mapValues { (name, capabilities) ->
                    Composition(name, capabilities.mapValues { it.value.toString() })
                }
            )
        }

        fun fromClasspath(resource: String): CompositionCatalog =
            CompositionCatalog::class.java.classLoader.getResourceAsStream(resource)
                ?.use { load(it) }
                ?: CompositionCatalog(emptyMap())
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -o -pl chat-core test -Dtest=CompositionCatalogTests`
Expected: PASS, 3 tests

- [ ] **Step 6: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/capability/CompositionCatalog.kt chat-core/src/test/kotlin/com/demo/chat/test/capability/CompositionCatalogTests.kt chat-core/src/test/resources/test-compositions.yml
git commit -m "feat: load blessed compositions from yaml"
```

---

### Task 3: The resolver

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityResolver.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityResolverTests.kt`

**Interfaces:**
- Consumes: `CapabilityRegistry`, `ProviderDescriptor` (Task 1); `CompositionCatalog`, `Composition` (Task 2)
- Produces:
  - `data class ResolvedCapability(val capability: String, val value: String, val source: String, val providerClass: String)`
  - `sealed interface ResolutionProblem` with `fun message(): String`, and cases `NotCovered`, `CoveredTwice`, `NoProvider`, `FlagsDisagree`
  - `data class CapabilityResolution(val resolved: Map<String, ResolvedCapability>, val problems: List<ResolutionProblem>)` with `val ok: Boolean` and `fun report(): String`
  - `object CapabilityResolver { fun resolve(registry: CapabilityRegistry, catalog: CompositionCatalog, selectedCompositions: List<String>, explicitSelectors: Map<String, String>, required: Set<String>): CapabilityResolution }`

- [ ] **Step 1: Write the failing test**

`chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityResolverTests.kt`:

```kotlin
package com.demo.chat.test.capability

import com.demo.chat.capability.CapabilityRegistry
import com.demo.chat.capability.CapabilityResolver
import com.demo.chat.capability.Composition
import com.demo.chat.capability.CompositionCatalog
import com.demo.chat.capability.ProviderDescriptor
import com.demo.chat.capability.ResolutionProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CapabilityResolverTests {

    private fun provider(capability: String, value: String) =
        ProviderDescriptor(capability, value, "com.demo.$capability.$value", null)

    private val registry = CapabilityRegistry(
        providers = listOf(
            provider("index", "lucene"),
            provider("key", "redis"),
            provider("pubsub", "redis-pubsub"),
            provider("persistence", "cassandra"),
            provider("persistence", "redis"),
            provider("secrets", "cassandra"),
            provider("secrets", "memory")
        ),
        consumers = emptyList()
    )

    private val catalog = CompositionCatalog(
        mapOf(
            "memory" to Composition("memory", mapOf("index" to "lucene")),
            "redis" to Composition("redis", mapOf("key" to "redis", "pubsub" to "redis-pubsub")),
            "cassandra" to Composition("cassandra", mapOf("persistence" to "cassandra", "secrets" to "cassandra")),
            "redis-store" to Composition("redis-store", mapOf("persistence" to "redis"))
        )
    )

    private val allFive = setOf("key", "persistence", "index", "pubsub", "secrets")

    @Test
    fun `partial compositions union into a complete cover`() {
        val resolution = CapabilityResolver.resolve(
            registry, catalog,
            selectedCompositions = listOf("memory", "redis", "cassandra"),
            explicitSelectors = emptyMap(),
            required = allFive
        )

        assertThat(resolution.ok).isTrue
        assertThat(resolution.resolved.mapValues { it.value.value })
            .containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    "key" to "redis",
                    "persistence" to "cassandra",
                    "index" to "lucene",
                    "pubsub" to "redis-pubsub",
                    "secrets" to "cassandra"
                )
            )
    }

    @Test
    fun `an uncovered capability is named with the values that could cover it`() {
        val resolution = CapabilityResolver.resolve(
            registry, catalog,
            selectedCompositions = listOf("memory", "redis"),
            explicitSelectors = emptyMap(),
            required = allFive
        )

        assertThat(resolution.ok).isFalse
        val problem = resolution.problems.filterIsInstance<ResolutionProblem.NotCovered>()
            .single { it.capability == "secrets" }
        assertThat(problem.availableValues).containsExactly("cassandra", "memory")
        assertThat(problem.message()).contains("secrets").contains("cassandra")
    }

    @Test
    fun `a capability claimed by two compositions names both`() {
        val resolution = CapabilityResolver.resolve(
            registry, catalog,
            selectedCompositions = listOf("cassandra", "redis-store"),
            explicitSelectors = emptyMap(),
            required = setOf("persistence")
        )

        val problem = resolution.problems.filterIsInstance<ResolutionProblem.CoveredTwice>().single()
        assertThat(problem.capability).isEqualTo("persistence")
        assertThat(problem.claims).containsExactlyInAnyOrder("cassandra", "redis-store")
    }

    @Test
    fun `a value nothing provides is a problem even when covered`() {
        val catalogWithTypo = CompositionCatalog(
            mapOf("typo" to Composition("typo", mapOf("persistence" to "redsi")))
        )

        val resolution = CapabilityResolver.resolve(
            registry, catalogWithTypo,
            selectedCompositions = listOf("typo"),
            explicitSelectors = emptyMap(),
            required = setOf("persistence")
        )

        val problem = resolution.problems.filterIsInstance<ResolutionProblem.NoProvider>().single()
        assertThat(problem.value).isEqualTo("redsi")
        assertThat(problem.availableValues).containsExactly("cassandra", "redis")
    }

    @Test
    fun `an explicit selector that contradicts its composition is a problem`() {
        val resolution = CapabilityResolver.resolve(
            registry, catalog,
            selectedCompositions = listOf("cassandra"),
            explicitSelectors = mapOf("persistence" to "redis"),
            required = setOf("persistence")
        )

        val problem = resolution.problems.filterIsInstance<ResolutionProblem.FlagsDisagree>().single()
        assertThat(problem.capability).isEqualTo("persistence")
        assertThat(problem.compositionValue).isEqualTo("cassandra")
        assertThat(problem.flagValue).isEqualTo("redis")
    }

    @Test
    fun `an explicit selector covers a capability no composition claims`() {
        val resolution = CapabilityResolver.resolve(
            registry, catalog,
            selectedCompositions = listOf("memory", "redis", "cassandra"),
            explicitSelectors = mapOf("persistence" to "cassandra"),
            required = allFive
        )

        assertThat(resolution.ok).isTrue
        assertThat(resolution.resolved["persistence"]!!.source).isEqualTo("cassandra")
    }

    @Test
    fun `an undeclared composition is named, not silently ignored`() {
        val resolution = CapabilityResolver.resolve(
            registry, catalog,
            selectedCompositions = listOf("memory", "postgres"),
            explicitSelectors = emptyMap(),
            required = setOf("index")
        )

        val problem = resolution.problems.filterIsInstance<ResolutionProblem.UnknownComposition>().single()
        assertThat(problem.name).isEqualTo("postgres")
        assertThat(problem.message()).contains("cassandra")
    }

    @Test
    fun `only required capabilities must be covered`() {
        val resolution = CapabilityResolver.resolve(
            registry, catalog,
            selectedCompositions = listOf("memory"),
            explicitSelectors = emptyMap(),
            required = setOf("index")
        )

        assertThat(resolution.ok).isTrue
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl chat-core test -Dtest=CapabilityResolverTests`
Expected: compilation failure — `Unresolved reference: CapabilityResolver`

- [ ] **Step 3: Write the resolver**

`chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityResolver.kt`:

```kotlin
package com.demo.chat.capability

data class ResolvedCapability(
    val capability: String,
    val value: String,
    /** The composition that supplied it, or "explicit" for a bare selector. */
    val source: String,
    val providerClass: String
)

sealed interface ResolutionProblem {
    fun message(): String

    data class NotCovered(
        val capability: String,
        val availableValues: List<String>,
        val selected: List<String>
    ) : ResolutionProblem {
        override fun message(): String = buildString {
            appendLine("Capability '$capability' is not covered.")
            appendLine("  compositions selected: ${selected.joinToString(", ").ifEmpty { "none" }}")
            appendLine("  providers available:   ${availableValues.joinToString(", ").ifEmpty { "none" }}")
            append("  cover it, or set app.service.core.$capability explicitly")
        }
    }

    data class CoveredTwice(
        val capability: String,
        val claims: List<String>
    ) : ResolutionProblem {
        override fun message(): String = buildString {
            appendLine("Capability '$capability' is covered twice.")
            append("  ${claims.sorted().joinToString(", ") { "$it[$capability]" }}")
        }
    }

    data class NoProvider(
        val capability: String,
        val value: String,
        val availableValues: List<String>
    ) : ResolutionProblem {
        override fun message(): String = buildString {
            appendLine("Capability '$capability' asked for '$value' and nothing on this classpath provides it.")
            append("  providers available: ${availableValues.joinToString(", ").ifEmpty { "none" }}")
        }
    }

    data class UnknownComposition(
        val name: String,
        val known: Set<String>
    ) : ResolutionProblem {
        override fun message(): String = buildString {
            appendLine("Composition '$name' is not declared in compositions.yml.")
            append("  declared: ${known.sorted().joinToString(", ").ifEmpty { "none" }}")
        }
    }

    data class FlagsDisagree(
        val capability: String,
        val composition: String,
        val compositionValue: String,
        val flagValue: String
    ) : ResolutionProblem {
        override fun message(): String = buildString {
            appendLine("Capability '$capability' is claimed by composition '$composition' as '$compositionValue'")
            append("  but the launch flag says '$flagValue'. Drop one of them.")
        }
    }
}

data class CapabilityResolution(
    val resolved: Map<String, ResolvedCapability>,
    val problems: List<ResolutionProblem>
) {
    val ok: Boolean get() = problems.isEmpty()

    fun report(): String = buildString {
        appendLine("Capability resolution:")
        resolved.toSortedMap().forEach { (capability, r) ->
            appendLine("  $capability = ${r.value}  (from ${r.source}, ${r.providerClass})")
        }
        if (problems.isNotEmpty()) {
            appendLine()
            problems.forEach { appendLine(it.message()) }
        }
    }
}

object CapabilityResolver {

    fun resolve(
        registry: CapabilityRegistry,
        catalog: CompositionCatalog,
        selectedCompositions: List<String>,
        explicitSelectors: Map<String, String>,
        required: Set<String>
    ): CapabilityResolution {
        val problems = mutableListOf<ResolutionProblem>()

        // capability -> composition names claiming it
        val claims = mutableMapOf<String, MutableList<String>>()
        selectedCompositions.forEach { name ->
            val composition = catalog.get(name) ?: run {
                problems += ResolutionProblem.UnknownComposition(name, catalog.names())
                return@forEach
            }
            composition.capabilities.keys.forEach { capability ->
                claims.getOrPut(capability) { mutableListOf() } += name
            }
        }

        claims.filterValues { it.size > 1 }.forEach { (capability, names) ->
            problems += ResolutionProblem.CoveredTwice(capability, names)
        }

        val chosen = mutableMapOf<String, Pair<String, String>>() // capability -> (value, source)

        claims.filterValues { it.size == 1 }.forEach { (capability, names) ->
            val composition = catalog.get(names.single())!!
            chosen[capability] = composition.capabilities.getValue(capability) to names.single()
        }

        explicitSelectors.forEach { (capability, flagValue) ->
            val fromComposition = chosen[capability]
            if (fromComposition != null && fromComposition.first != flagValue) {
                problems += ResolutionProblem.FlagsDisagree(
                    capability = capability,
                    composition = fromComposition.second,
                    compositionValue = fromComposition.first,
                    flagValue = flagValue
                )
            } else if (fromComposition == null) {
                chosen[capability] = flagValue to "explicit"
            }
        }

        val resolved = mutableMapOf<String, ResolvedCapability>()
        chosen.forEach { (capability, valueAndSource) ->
            val (value, source) = valueAndSource
            val provider = registry.providerFor(capability, value)
            if (provider == null) {
                problems += ResolutionProblem.NoProvider(capability, value, registry.valuesFor(capability))
            } else {
                resolved[capability] = ResolvedCapability(capability, value, source, provider.className)
            }
        }

        required.filterNot { chosen.containsKey(it) }.sorted().forEach { capability ->
            problems += ResolutionProblem.NotCovered(
                capability = capability,
                availableValues = registry.valuesFor(capability),
                selected = selectedCompositions
            )
        }

        return CapabilityResolution(resolved, problems)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o -pl chat-core test -Dtest=CapabilityResolverTests`
Expected: PASS, 8 tests

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityResolver.kt chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityResolverTests.kt
git commit -m "feat: resolve capabilities from compositions and selectors"
```

---

### Task 4: Environment post-processor, reporting only

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityEnvironmentPostProcessor.kt`
- Create: `chat-core/src/main/resources/META-INF/spring.factories`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityEnvironmentPostProcessorTests.kt`

**Interfaces:**
- Consumes: everything from Tasks 1-3
- Produces:
  - `class CapabilityEnvironmentPostProcessor : EnvironmentPostProcessor`
  - Reads: `app.deployment.compositions` (comma-separated), `app.service.core.<capability>` for every capability the registry knows, `app.capability.enforce` (boolean, default `false`), `app.capability.base-packages` (comma-separated, default `com.demo.chat`)
  - Writes: property `app.capability.report` into a property source named `capability-resolution`
  - Required capabilities are those declared by a `@RequiresCapability` whose condition property is present in the Environment, or absent with `matchIfMissing = true`

- [ ] **Step 1: Write the failing test**

`chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityEnvironmentPostProcessorTests.kt`:

```kotlin
package com.demo.chat.test.capability

import com.demo.chat.capability.CapabilityEnvironmentPostProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.mock.env.MockEnvironment

class CapabilityEnvironmentPostProcessorTests {

    private fun process(vararg properties: Pair<String, String>): MockEnvironment {
        val environment = MockEnvironment()
        properties.forEach { (k, v) -> environment.setProperty(k, v) }
        environment.setProperty("app.capability.base-packages", "com.demo.chat.test.capability.fixture")
        CapabilityEnvironmentPostProcessor().postProcessEnvironment(environment, SpringApplication())
        return environment
    }

    @Test
    fun `reports what resolved`() {
        val environment = process(
            "app.service.core.persistence" to "fixture-a",
            "app.service.core.index" to "fixture-idx"
        )

        val report = environment.getProperty("app.capability.report")!!
        assertThat(report).contains("persistence = fixture-a")
        assertThat(report).contains("index = fixture-idx")
    }

    @Test
    fun `reports a problem without throwing when enforcement is off`() {
        val environment = process("app.controller.persistence" to "")

        assertThat(environment.getProperty("app.capability.report"))
            .contains("Capability 'persistence' is not covered")
    }

    @Test
    fun `requires a capability only when its consumer condition is satisfied`() {
        val environment = process("app.service.core.index" to "fixture-idx")

        assertThat(environment.getProperty("app.capability.report"))
            .doesNotContain("is not covered")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl chat-core test -Dtest=CapabilityEnvironmentPostProcessorTests`
Expected: compilation failure — `Unresolved reference: CapabilityEnvironmentPostProcessor`

- [ ] **Step 3: Write the post-processor**

`chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityEnvironmentPostProcessor.kt`:

```kotlin
package com.demo.chat.capability

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * Resolves capabilities before the context is created.
 *
 * This runs as an EnvironmentPostProcessor rather than a bean because its result
 * has to be available before auto-configuration decides anything.
 */
class CapabilityEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication
    ) {
        val basePackages = environment
            .getProperty("app.capability.base-packages", "com.demo.chat")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val registry = CapabilityScanner.scan(basePackages)
        val catalog = CompositionCatalog.fromClasspath("compositions.yml")

        val compositions = environment
            .getProperty("app.deployment.compositions", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val explicit = registry.capabilities()
            .mapNotNull { capability ->
                environment.getProperty("app.service.core.$capability")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { capability to it }
            }
            .toMap()

        val resolution = CapabilityResolver.resolve(
            registry = registry,
            catalog = catalog,
            selectedCompositions = compositions,
            explicitSelectors = explicit,
            required = requiredCapabilities(registry, environment)
        )

        environment.propertySources.addFirst(
            MapPropertySource("capability-resolution", mapOf("app.capability.report" to resolution.report()))
        )

        if (!resolution.ok && environment.getProperty("app.capability.enforce", Boolean::class.java, false)) {
            throw IllegalStateException("\n" + resolution.report())
        }
    }

    private fun requiredCapabilities(
        registry: CapabilityRegistry,
        environment: ConfigurableEnvironment
    ): Set<String> =
        registry.consumers
            .filter { active(it.condition, environment) }
            .flatMap { it.capabilities }
            .toSet()

    private fun active(condition: ConditionDescriptor?, environment: ConfigurableEnvironment): Boolean {
        if (condition == null) return true
        val value = environment.getProperty(condition.property) ?: return condition.matchIfMissing
        return condition.havingValue.isEmpty() || condition.havingValue == value
    }
}
```

- [ ] **Step 4: Register it**

`chat-core/src/main/resources/META-INF/spring.factories`:

```properties
org.springframework.boot.env.EnvironmentPostProcessor=\
com.demo.chat.capability.CapabilityEnvironmentPostProcessor
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -o -pl chat-core test -Dtest=CapabilityEnvironmentPostProcessorTests`
Expected: PASS, 3 tests

- [ ] **Step 6: Verify nothing else broke**

The post-processor now runs in every Spring context in the tree, including every existing test, with enforcement off.

Run: `./shell-scripts/build-health.sh`
Expected: exit 0, `reality matches docs/BUILD-HEALTH.md`

- [ ] **Step 7: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityEnvironmentPostProcessor.kt chat-core/src/main/resources/META-INF/spring.factories chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityEnvironmentPostProcessorTests.kt
git commit -m "feat: resolve capabilities before the context starts, reporting only"
```

---

### Task 5: Annotate the real providers

**Files:** each row is one `@ProvidesCapability` to add, above the condition already on the class.

| File | capability | value |
|------|-----------|-------|
| `chat-persistence-memory/.../memory/MemoryKeyServices.kt` | `key` | `memory` |
| `chat-persistence-memory/.../memory/MemoryPersistenceServices.kt` | `persistence` | `memory` |
| `chat-persistence-memory/.../memory/MemorySecretsStoreServiceBeans.kt` | `secrets` | `memory` |
| `chat-persistence-redis/.../redis/RedisKeyServices.kt` | `key` | `redis` |
| `chat-persistence-redis/.../redis/RedisPersistenceServices.kt` | `persistence` | `redis` |
| `chat-persistence-cassandra/.../cassandra/CoreKeyServices.kt` | `key` | `cassandra` |
| `chat-persistence-cassandra/.../cassandra/CorePersistenceServices.kt` | `persistence` | `cassandra` |
| `chat-persistence-cassandra/.../cassandra/SecretStoreConfig.kt` | `secrets` | `cassandra` |
| `chat-index-lucene/src/main/kotlin/com/demo/chat/config/LuceneIndexBeans.kt` | `index` | `lucene` |
| `chat-index-cassandra/.../index/cassandra/IndexServiceConfiguration.kt` | `index` | `cassandra` |
| `chat-messaging-memory/.../pubsub/memory/MemoryPubSubBeans.kt` | `pubsub` | `memory` |
| `chat-messaging-kafka/.../pubsub/kafka/KafkaPubSubBeans.kt` | `pubsub` | `kafka` |
| `chat-persistence-xstream/.../pubsub/redis/RedisPubSubBeans.kt` | `pubsub` | `redis-pubsub` |
| `chat-persistence-xstream/.../pubsub/redis/XStreamPubSubBeans.kt` | `pubsub` | `redis-xstream` |

- Modify: `chat-deploy/pom.xml` (test-scoped dependencies, see Step 2)
- Test: `chat-deploy/src/test/kotlin/com/demo/chat/test/capability/CapabilityAgreementTests.kt`

The three `KeyGenConfiguration` classes are **not** in this table. They are gated on
`app.service.core.key` but supply a key *generator* rather than the `key` capability
itself, which `*KeyServices` supplies. Annotating both would trip the duplicate check
in this task's second test.

Note the `pubsub` values `redis-pubsub` and `redis-xstream` are supplied by
`chat-persistence-xstream`, not `chat-persistence-redis`. The module name and the
capability value disagree, which is exactly the sort of thing this registry makes
visible.

**Interfaces:**
- Consumes: `ProvidesCapability` (Task 1), `CapabilityScanner`, `CapabilityRegistry`
- Produces: every real provider carries an annotation whose capability and value match its `@ConditionalOnProperty`

- [ ] **Step 1: Find every provider that needs annotating**

Run:

```bash
grep -rn "app.service.core" --include='*.kt' . | grep -v target | grep -i "conditionalonproperty\|prefix"
```

Every hit is a provider. The capability is the `name` attribute, the value is `havingValue`.

- [ ] **Step 2: Put the provider modules on a test classpath**

The test has to see every provider at once. It cannot live in `chat-core`, because
every provider module depends on `chat-core` and adding them back would be a cycle.
It goes in `chat-deploy`, which already depends on `chat-core` and is depended on
only by deploy modules.

Add to `chat-deploy/pom.xml`, all with `<scope>test</scope>` and `<version>0.0.1</version>`:
`chat-persistence-memory`, `chat-persistence-redis`, `chat-persistence-cassandra`,
`chat-index-lucene`, `chat-index-cassandra`, `chat-messaging-memory`,
`chat-messaging-kafka`, `chat-persistence-xstream`.

- [ ] **Step 3: Write the failing agreement test**

`chat-deploy/src/test/kotlin/com/demo/chat/test/capability/CapabilityAgreementTests.kt`:

```kotlin
package com.demo.chat.test.capability

import com.demo.chat.capability.CapabilityScanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The annotation and the condition state the same pair twice and can disagree.
 * This test is the whole mitigation for that.
 */
class CapabilityAgreementTests {

    private val registry = CapabilityScanner.scan(listOf("com.demo.chat.config"))

    @Test
    fun `every provider's annotation matches the condition beside it`() {
        val disagreements = registry.providers
            .filter { it.condition != null }
            .filterNot { provider ->
                provider.condition!!.property.endsWith(".${provider.capability}") &&
                    provider.condition!!.havingValue == provider.value
            }
            .map { "${it.className}: @ProvidesCapability(${it.capability}, ${it.value}) vs ${it.condition}" }

        assertThat(disagreements).isEmpty()
    }

    @Test
    fun `the scan finds the providers this deployment ships`() {
        assertThat(registry.capabilities())
            .contains("key", "persistence", "index", "pubsub", "secrets")
    }

    @Test
    fun `no capability value is provided twice`() {
        val duplicates = registry.providers
            .groupBy { it.capability to it.value }
            .filterValues { it.size > 1 }
            .map { (key, providers) -> "$key provided by ${providers.map { it.className }}" }

        assertThat(duplicates).isEmpty()
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `mvn -o -pl chat-core,chat-deploy test -Dtest=CapabilityAgreementTests`
Expected: FAIL on `the scan finds the providers this deployment ships` — the
capability list is empty, because nothing is annotated yet. The other two tests
pass vacuously over an empty registry, which is why that third test exists.

- [ ] **Step 5: Annotate each provider**

Work down the table under **Files**, adding the annotation above the existing
condition on each class. Example, `chat-persistence-redis`:

```kotlin
@ProvidesCapability(capability = "persistence", value = "redis")
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "redis")
class RedisPersistenceServices<T, V>(...) : PersistenceServiceBeans<T, V> {
```

and `chat-index-lucene`:

```kotlin
@ProvidesCapability(capability = "index", value = "lucene")
@ConditionalOnProperty(
    prefix = "app.service.core",
    name = ["index"],
    havingValue = "lucene",
    matchIfMissing = true
)
class LuceneIndexBeans<T, V, Q>(...) : IndexServiceBeans<T, V, Q> {
```

The import is `com.demo.chat.capability.ProvidesCapability` in every case.

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -o -pl chat-core,chat-deploy test -Dtest=CapabilityAgreementTests`
Expected: PASS, 3 tests

- [ ] **Step 7: Verify the whole tree**

Run: `./shell-scripts/build-health.sh`
Expected: exit 0

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: declare capability coverage on every provider"
```

---

### Task 6: Bless today's compositions

**Files:**
- Create: `chat-deploy/src/main/resources/compositions.yml`
- Test: `chat-deploy/src/test/kotlin/com/demo/chat/test/capability/CompositionCoverageTests.kt`

**Interfaces:**
- Consumes: `CompositionCatalog` (Task 2), `CapabilityScanner` (Task 1)
- Produces: `compositions.yml` on the runtime classpath, loaded by `CompositionCatalog.fromClasspath("compositions.yml")` in Task 4

- [ ] **Step 1: Write the failing test**

`chat-deploy/src/test/kotlin/com/demo/chat/test/capability/CompositionCoverageTests.kt`:

```kotlin
package com.demo.chat.test.capability

import com.demo.chat.capability.CapabilityScanner
import com.demo.chat.capability.CompositionCatalog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompositionCoverageTests {

    private val catalog = CompositionCatalog.fromClasspath("compositions.yml")
    private val registry = CapabilityScanner.scan(listOf("com.demo.chat.config"))

    @Test
    fun `the blessed compositions are declared`() {
        assertThat(catalog.names())
            .contains("memory", "lucene", "redis", "redis-xstream", "cassandra", "kafka")
    }

    @Test
    fun `every value a composition names has a provider`() {
        val unprovided = catalog.compositions.values.flatMap { composition ->
            composition.capabilities.map { (capability, value) -> composition.name to (capability to value) }
        }.filterNot { (_, pair) -> registry.providerFor(pair.first, pair.second) != null }
            .map { (name, pair) -> "$name claims ${pair.first}=${pair.second} and nothing provides it" }

        assertThat(unprovided).isEmpty()
    }

    @Test
    fun `memory and lucene cover the five core capabilities exactly once`() {
        val union = listOf("memory", "lucene").flatMap { catalog.get(it)!!.capabilities.keys }

        assertThat(union).containsExactlyInAnyOrder("key", "persistence", "index", "pubsub", "secrets")
    }

    @Test
    fun `redis and lucene leave exactly one capability for an explicit selector`() {
        val covered = listOf("redis", "lucene").flatMap { catalog.get(it)!!.capabilities.keys }.toSet()

        assertThat(setOf("key", "persistence", "index", "pubsub", "secrets") - covered)
            .containsExactly("secrets")
    }

    @Test
    fun `no two compositions that ship together claim the same capability`() {
        val overlaps = listOf(listOf("memory", "lucene"), listOf("redis", "lucene"))
            .flatMap { selection ->
                selection.flatMap { catalog.get(it)!!.capabilities.keys }
                    .groupingBy { it }.eachCount()
                    .filterValues { it > 1 }
                    .keys
                    .map { "$selection claims $it twice" }
            }

        assertThat(overlaps).isEmpty()
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl chat-core,chat-deploy test -Dtest=CompositionCoverageTests`
Expected: FAIL — the catalog is empty, `contains("memory", ...)` fails

- [ ] **Step 3: Write the compositions**

`chat-deploy/src/main/resources/compositions.yml`:

```yaml
# A composition names only the capabilities its own backend serves, and nothing
# else. The matrix is sparse - memory has no index, redis has neither index nor
# secrets - so a working deployment unions several of these. A capability claimed
# by two selected compositions is an error, not a precedence question, so the
# compositions here never overlap by accident.
#
#   memory + lucene                  -> the old --memory deployment
#   redis + lucene + secrets=memory  -> the old --redis deployment
#   cassandra                        -> covers four; still needs a pubsub
compositions:
  memory:
    key: memory
    persistence: memory
    pubsub: memory
    secrets: memory
  lucene:
    index: lucene
  redis:
    key: redis
    persistence: redis
    pubsub: redis-pubsub
  redis-xstream:
    pubsub: redis-xstream
  cassandra:
    key: cassandra
    persistence: cassandra
    index: cassandra
    secrets: cassandra
  kafka:
    pubsub: kafka
```

`index: lucene` is its own composition rather than a line inside `memory`, because
Lucene is a separate provider module and every non-cassandra backend needs it. That
is the sparseness of the matrix showing through, and it is the thing the old module
names concealed: `--redis` always meant redis plus Lucene plus memory secrets.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o -pl chat-core,chat-deploy test -Dtest=CompositionCoverageTests`
Expected: PASS, 5 tests

If `every value a composition names has a provider` fails, the yaml and the
annotations from Task 5 disagree about a value — most likely `redis-pubsub` or
`redis-xstream`, which are supplied by `chat-persistence-xstream`. Fix the yaml or
the annotation, never the test.

- [ ] **Step 5: Commit**

```bash
git add chat-deploy/src/main/resources/compositions.yml chat-deploy/src/test/kotlin/com/demo/chat/test/capability/CompositionCoverageTests.kt
git commit -m "feat: bless the compositions that exist today"
```

---

### Task 7: Enforce

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/capability/CapabilityEnvironmentPostProcessor.kt` (default `app.capability.enforce` to `true`)
- Modify: `chat-persistence-memory/src/main/kotlin/com/demo/chat/config/persistence/memory/MemoryPersistenceServices.kt`, `MemoryKeyServices.kt`, `MemorySecretsStoreServiceBeans.kt` (remove `matchIfMissing`)
- Modify: `chat-messaging-memory/src/main/kotlin/com/demo/chat/config/pubsub/memory/MemoryPubSubBeans.kt` (remove `matchIfMissing`)
- Modify: `chat-index-lucene/src/main/kotlin/com/demo/chat/config/LuceneIndexBeans.kt` (remove `matchIfMissing`)
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityEnforcementTests.kt`

**Interfaces:**
- Consumes: everything above
- Produces: a startup failure naming the capability, replacing a `NoSuchBeanDefinitionException` five layers downstream

- [ ] **Step 1: Write the failing test**

`chat-core/src/test/kotlin/com/demo/chat/test/capability/CapabilityEnforcementTests.kt`:

```kotlin
package com.demo.chat.test.capability

import com.demo.chat.capability.CapabilityEnvironmentPostProcessor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.mock.env.MockEnvironment

class CapabilityEnforcementTests {

    private fun environmentWith(vararg properties: Pair<String, String>) = MockEnvironment().apply {
        properties.forEach { (k, v) -> setProperty(k, v) }
        setProperty("app.capability.base-packages", "com.demo.chat.test.capability.fixture")
    }

    @Test
    fun `an uncovered capability refuses to start and names itself`() {
        val environment = environmentWith("app.controller.persistence" to "")

        assertThatThrownBy {
            CapabilityEnvironmentPostProcessor().postProcessEnvironment(environment, SpringApplication())
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Capability 'persistence' is not covered")
            .hasMessageContaining("fixture-a")
    }

    @Test
    fun `enforcement can be switched off explicitly`() {
        val environment = environmentWith(
            "app.controller.persistence" to "",
            "app.capability.enforce" to "false"
        )

        CapabilityEnvironmentPostProcessor().postProcessEnvironment(environment, SpringApplication())

        assertThat(environment.getProperty("app.capability.report")).contains("is not covered")
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl chat-core test -Dtest=CapabilityEnforcementTests`
Expected: FAIL — nothing is thrown, because enforcement defaults to `false`

- [ ] **Step 3: Flip the default**

In `CapabilityEnvironmentPostProcessor`, change:

```kotlin
        if (!resolution.ok && environment.getProperty("app.capability.enforce", Boolean::class.java, false)) {
```

to:

```kotlin
        if (!resolution.ok && environment.getProperty("app.capability.enforce", Boolean::class.java, true)) {
```

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -o -pl chat-core test -Dtest=CapabilityEnforcementTests`
Expected: PASS, 2 tests

- [ ] **Step 5: Find what the tree was relying on defaults for**

Run: `./shell-scripts/build-health.sh`
Expected: FAIL, with modules whose tests never named their capabilities. The report names each one.

- [ ] **Step 6: Make every launch explicit**

For each failing test class, add the capabilities it needs to its `@TestPropertySource`, for example:

```kotlin
@TestPropertySource(
    properties = [
        "app.service.core.key=memory",
        "app.service.core.persistence=memory",
        "app.service.core.index=lucene",
        "app.service.core.pubsub=memory",
        "app.service.core.secrets=memory"
    ]
)
```

Prefer `"app.deployment.compositions=memory"` where the test wants the whole memory backend, which is the same five values from one line.

- [ ] **Step 7: Remove matchIfMissing**

In each file listed under **Files**, delete the `matchIfMissing = true` argument. Example:

```kotlin
@ConditionalOnProperty(
    prefix = "app.service.core",
    name = ["persistence"],
    havingValue = "memory"
)
```

An unset selector now fails in the resolver with a named capability, rather than silently selecting memory.

- [ ] **Step 8: Verify both modes**

Run: `./shell-scripts/build-health.sh`
Expected: exit 0

Run: `./shell-scripts/build-health.sh --integration`
Expected: exit 0

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: refuse to start when a required capability is uncovered"
```

---

## Verification

After Task 7 the following must all hold:

- `./shell-scripts/build-health.sh` exits 0
- `./shell-scripts/build-health.sh --integration` exits 0
- No module has been deleted, no image name has changed, no selector has been renamed
- A deployment that names no capabilities fails at startup with a message naming the first uncovered one, rather than resolving to memory
