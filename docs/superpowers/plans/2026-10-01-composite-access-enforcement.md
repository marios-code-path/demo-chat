# Composite access enforcement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Spring method security run on the three composite access
interfaces, so a deployed composition refuses a caller who holds no grant.

**Architecture:** Each controller declares the matching annotated `*ServiceAccess`
interface by Kotlin interface delegation. The controller bean then carries the
annotation, and the dispatcher holds the proxy. Two probes measure that claim
before any further work, because the boundary is the whole design.

**Tech Stack:** Kotlin 2.4.20, JDK 25, Spring Boot 4.0.8, Spring Security 7,
Spring Shell, Reactor 3.8, Maven, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-10-01-composite-access-enforcement-design.md`

## Global Constraints

- **The boundary is the whole design.** If a transport dispatch does not cross
  the proxy, stop and return the design to the owner. Do not widen the tests
  until they pass.
- **`topic-by-name` is fail-closed.** The owner decided this on 2026-10-01. It
  denies every caller and no grant reaches it. `CHAT-dgjhljbl` restores it.
- **No grant changes.** `shared-deploy-configuration/src/main/config/userinit.yml`
  changes no line. It carries an uncommitted edit from another line of work.
- **`CHAT-eoqkbqve` lands first, on its own commit set.** Its commits name that
  issue. Every other commit names `CHAT-znprrzhn`.
- **Every test names its caller state.** RSocket with no credential reaches the
  `Anon` identity. No security context reaches no identity. A denied
  authenticated caller reaches its user identity and lacks the grant.
- **Every denial test also asserts zero downstream calls.** A refusal and a
  broken route are indistinguishable without it.
- **Run `mvn -o -pl chat-core,<module> test`.** Never `-pl <module>` alone.
- **One Maven build per worktree.** A concurrent `clean` deletes the other
  build's `target` and fakes a classpath failure.
- **Redirect Maven output to a file.** Read the exit code and the summary lines.
- **Merge commits only.** The owner merges by pull request, with an
  owner-named `--admin` bypass.
- **Controlled English** for every comment, commit message, and document.
- **Do not overwrite `CompositeControllersConfiguration.kt`.** A prior
  classifier denial covers destroying that file. The change here is three
  targeted line edits that add one supertype to each class declaration.
## Review Focus

Five inputs the spec implies and no task's tests exercise by default. Each line
names where it is pinned.

1. **A caller holding a domain wildcard on a write.** `{User, MessageTopic,
   ALL}` does not cover `SEND`, because `ALL` is a literal. Pinned at the
   expression level by `a message root row does not cover a send to a room` in
   `SendCheckExpressionTests`, in chat-security.
2. **An id that the registry does not hold.** `hasAccessToId` resolves through
   the registry, and an unknown id denies with no error. Pinned by the key
   verifier tests of chat-security, and by the denied probe of Task 1, which
   fails if the resolution throws instead of denying.
3. **`getRoomByName` has no target key.** The request carries a name and no id,
   so no target check can be honest. The route denies every caller, and it
   never reads the topic. Pinned by `the room by name expression denies a fully
   privileged caller` in Task 5.
4. **The REST facade methods call the annotated methods on the same object.**
   `restGetRoom`, `restDeleteRoom`, `joinRestRoom`, `leaveRestRoom` and
   `restRoomMembers` are declared on the mapping interface and call its own
   members. Pinned by `a denied caller is refused at the facade route and the
   service is never called` in Task 2.
5. **A streaming route.** `listenTopic` and `listRooms` return a `Flux`. A
   refusal must arrive before any element does. Pinned by the denied probe of
   Task 1, which reads `AccessDeniedException` on the `topic-list` route.

**The transport probes prove the crossing, and not the grants.** A probe with a
mocked broker answers one permission. The grant semantics stay measured in
chat-security, where the store and the index are real. Do not re-derive a grant
rule from a probe.

## Plan corrections, 2026-10-01

The first execution of Tasks 1 and 2 stopped at a defect in the probe design.
The audit below found nine defect classes. Every one is repaired in this
document. `CHAT-eoqkbqve` is merged, and Tasks 3 to 5 are done.

**A reader must not treat this section as the record of what changed.** The
tasks below are the plan. A defect that the tasks no longer carry is repaired,
and a defect named here alone is not.

| # | Defect | Repair |
|---|---|---|
| 1 | The annotated interface was a delegation target, `TopicServiceAccess<T, V> by b.topicService()`. `b.topicService()` answers `ChatTopicService<T, V>`, so the clause cannot compile, and no object implements the annotated type. Tasks 1, 2, 6 and 8 carried it. | The annotated interface is a plain supertype, and the existing clause delegates the plain service. The spec records this shape, and `SecretsControllerTests` measures it. |
| 2 | One `probe.allow` property drove an allowed caller and a denied caller in one cached Spring context. One of the two tests always ran against the wrong answer. | One probe class per answer state, and no system property. Each class holds a fixed answer. |
| 3 | `onErrorReturn(emptyList())` made a refusal and an empty room list the same answer. The allowed test asserted `isNotNull`, which an empty list satisfies. **Both tests passed in every state, so the probe could not fail.** | The denied test reads `AccessDeniedException` through `StepVerifier`. The allowed test asserts a size and a call count. |
| 4 | The zero downstream assertion named a real service object, so Mockito raised `NotAMockException`. Task 1 also named `listRooms` on `AccessBroker`, which declares no such method. | The probe supplies `TestCompositeServiceBeans`, which holds Mockito mocks. The controller and the assertion share that instance. |
| 5 | The probe bean was named `probeChatAccess`. Every expression resolves the name `chatAccess`, so the expressions would not resolve. | Task 1 reuses `ChatAccessTestConfiguration`, which already supplies the right name outside the scanned package. Task 2 declares its own bean named `chatAccess`, because that class is not on the chat-webflux test classpath. |
| 6 | Review Focus named four tests in Task 7 that a single fixed answer cannot distinguish, so none could fail. | Those concerns are pinned where they are measurable. The probes carry the crossing, and chat-security carries the grants. |
| 7 | The Kotlin of Task 1 was not valid. Its test methods sat outside a class body, above the class declaration. | Each probe class is complete and self-contained. |
| 8 | Task 6 declared `UserServiceController<T>(b: CompositeServiceBeans<T>)`. The interface takes two type parameters. Task 10 said four shell call sites and listed five. | Both corrected. |
| 9 | Task 2's slice supplied no `chatAccess` bean and enabled no method security, so the facade route could not be refused at all. Its Expected line read "both tests PASS", and one of the two could never pass. | Task 2 gained the same two-class rewrite as Task 1. Its Step 4 reads four outcomes by a table, and one of them stops the plan. |

**Both probes were rewritten, and not Task 1 alone.** The first audit named
Task 1, and the same defect class sat in Task 2 in full.

One correction is not a defect. **`TestLongCompositeServiceBeans` exists**, and
it is declared inside `LongBeans.kt` rather than in a file of its own.

---

### Task 1: The RSocket boundary probe

The gate. One route, one allowed caller, one denied caller. If the allowed
caller is refused, or the denied caller reaches the downstream service, the
boundary is wrong and the work stops here.

**Files:**
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt:26-29`
- Create: `chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/probe/RSocketBoundaryProbeDeniedTests.kt`
- Create: `chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/probe/RSocketBoundaryProbeAllowedTests.kt`

**Interfaces:**
- Consumes: `TopicServiceAccess<T, V>` from `com.demo.chat.security.access.composite`
- Produces: nothing. This task answers a question.

**Two test classes, and not one.** The answer comes from a mocked `AccessBroker`,
and Spring caches the context per test class. One class with one mocked answer
can hold an allowed caller or a denied caller. It cannot hold both. The original
draft of this task used one `probe.allow` property for both states, so one of the
two tests always ran against the wrong answer.

- [ ] **Step 1: Add the annotated supertype to `TopicServiceController`**

**The annotated interface is a supertype, and it is not a delegation target.**
`b.topicService()` answers a `ChatTopicService<T, V>`. The annotated
`TopicServiceAccess<T, V>` is a different type, and no object implements it. A
delegation clause cannot bind it, and a cast would throw at the first call.

Two measured precedents hold the right shape:

```kotlin
// SecretsControllerTests, chat-service-controller test source.
class TestSecretStoreController<T>(private val that: SecretsStore<T>, private val verifier: KeyVerifier<T>) :
    SecretsStoreMapping<T>, SecretsStoreAccess<T>, SecretsStore<T> by that
```

The spec records the same shape under `What this pass wires`. Add one supertype
to the controller. Keep the existing delegation clause unchanged.

```kotlin
@ConditionalOnProperty(prefix = "app.controller", name = ["topic"])
@Controller
@MessageMapping("topic")
class TopicServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    TopicServiceControllerMapping<T, V>,
    TopicServiceAccess<T, V>,
    ChatTopicService<T, V> by b.topicService()
```

Add the import for `TopicServiceAccess`. The class file already imports it.

- [ ] **Step 2: Write the denied probe**

The probe sits in `com.demo.chat.test.rsocket.probe`, and not in
`com.demo.chat.test.rsocket`. That parent package holds a bare `@ComponentScan`,
so a component there enters every other RSocket test context.

```kotlin
package com.demo.chat.test.rsocket.probe

import com.demo.chat.config.controller.composite.TopicServiceController
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.test.access.ChatAccessTestConfiguration
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketSecurityTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration

/**
 * The denied caller at the RSocket boundary for `CHAT-znprrzhn`.
 *
 * `topic-list` carries `hasAccessToDomain('MessageTopic', 'ALL')`. The mocked
 * broker answers false. So the proxy must refuse, and the delegate must never
 * see the call.
 *
 * **The refusal alone proves nothing.** An unevaluable expression also refuses.
 * The zero downstream assertion is what separates a refusal from a broken route.
 */
@ContextConfiguration(
    classes = [
        TopicServiceController::class,
        RSocketSecurityTestConfiguration::class,
        ChatAccessTestConfiguration::class,
        ProbeServiceBeans::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RSocketBoundaryProbeDeniedTests : RSocketTestBase() {

    @MockitoBean private lateinit var accessBroker: AccessBroker<Long>
    @MockitoBean private lateinit var rootKeys: RootKeys<Long>

    @Autowired private lateinit var probe: ProbeServiceBeans

    @Test
    fun `a denied caller is refused and listRooms is never called`() {
        given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        StepVerifier.create(
            requester.route("topic-list").retrieveFlux(MessageTopic::class.java)
        ).expectError(AccessDeniedException::class.java)
            .verify(Duration.ofSeconds(10))

        verify(probe.mockTopicBean, never()).listRooms()
    }
}
```

- [ ] **Step 3: Write the allowed probe**

The allowed probe is the control. It is the same class with one fixed answer
changed, so the two runs differ in nothing else.

```kotlin
package com.demo.chat.test.rsocket.probe

import com.demo.chat.config.controller.composite.TopicServiceController
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.test.access.ChatAccessTestConfiguration
import com.demo.chat.test.anyObject
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.rsocket.RSocketSecurityTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito.given
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The allowed caller at the RSocket boundary for `CHAT-znprrzhn`.
 *
 * **This is the control.** The mocked broker answers true, so a refusal here
 * means the proxy never saw the annotation. The delegate answers one room, so
 * the assertion reads a value and not an emptiness.
 */
@ContextConfiguration(
    classes = [
        TopicServiceController::class,
        RSocketSecurityTestConfiguration::class,
        ChatAccessTestConfiguration::class,
        ProbeServiceBeans::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RSocketBoundaryProbeAllowedTests : RSocketTestBase() {

    @MockitoBean private lateinit var accessBroker: AccessBroker<Long>
    @MockitoBean private lateinit var rootKeys: RootKeys<Long>

    @Autowired private lateinit var probe: ProbeServiceBeans

    @Test
    fun `an allowed caller reaches the service and listRooms runs once`() {
        given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))

        val hits = requester.route("topic-list")
            .retrieveFlux(MessageTopic::class.java)
            .collectList()
            .block(Duration.ofSeconds(10))

        assertThat(hits).describedAs("the allowed route answered").hasSize(1)
        verify(probe.mockTopicBean, times(1)).listRooms()
    }
}
```

Both probes share one configuration, which supplies a mockable delegate.

```kotlin
package com.demo.chat.test.rsocket.probe

import com.demo.chat.test.config.TestCompositeServiceBeans
import org.mockito.BDDMockito.given
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux

/**
 * The delegate that the controller reaches through Kotlin interface
 * delegation.
 *
 * `TestCompositeServiceBeans` holds Mockito mocks, so `mockTopicBean` is the
 * same object that `b.topicService()` answers. The zero downstream assertion
 * needs that identity. A real service would throw `NotAMockException`.
 */
@TestConfiguration
class ProbeServiceBeans {

    @Bean
    fun compositeServiceBeans(): TestCompositeServiceBeans<Long, String> =
        TestCompositeServiceBeans<Long, String>().also {
            given(it.mockTopicBean.listRooms()).willReturn(Flux.empty())
        }
}
```

The `chatAccess` bean name comes from `ChatAccessTestConfiguration`, which
already supplies it and already sits outside the scanned package. **Do not name
that bean anything else.** The expressions resolve the name `chatAccess`, so a
bean named `probeChatAccess` leaves every expression unresolvable.

- [ ] **Step 4: Run both probes**

Run: `mvn -o -pl chat-core,chat-service-controller -Dtest='RSocketBoundaryProbe*Tests' -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/probe.log 2>&1; echo "EXIT=$?"; tail -40 /tmp/probe.log`

Expected: PASS, 2 tests, 0 skipped. The allowed caller receives one room. The
denied caller sees `AccessDeniedException` and the delegate is untouched.

- [ ] **Step 5: Read the result**

- **The allowed caller is refused.** The proxy does not read the annotation
  from a supertype interface. **STOP.** Record the finding on `CHAT-znprrzhn`
  and return the design to the owner.
- **The denied caller reaches the service.** The dispatcher holds the raw
  delegate and not the proxy. **STOP.** Same route.
- **A probe skips.** Read the skipped count, not the exit code. A skipped probe
  proves nothing.
- **Both pass.** The boundary holds. Continue to Task 2.

- [ ] **Step 6: Commit**

```bash
git add chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt \
        chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/probe/
git commit -m "Measure the RSocket dispatch against the method security proxy

The boundary probe for CHAT-znprrzhn. Two probe classes hold the allowed
caller and the denied caller in separate contexts, so each answer is fixed.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: The REST boundary probe

The second gate. The webflux mapping interface declares five facade methods
that call its own members, so a route can cross the proxy twice or not at all.

**Files:**
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatTopicServiceController.kt:10-11`
- Create: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeAllowedTests.kt`
- Create: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeDeniedTests.kt`

**Interfaces:**
- Consumes: `TopicServiceAccess<T, String>` from `com.demo.chat.security.access.composite`
- Produces: nothing. This task answers a question.
- [ ] **Step 1: Write the two probe classes**

**This slice supplies no `chatAccess` bean and enables no method security.**
That is what the probe measures. Two classes hold one fixed answer each, so
neither answer depends on a system property.

**`WebFluxTestConfiguration` builds a permit-all chain on purpose.** Its KDoc
states that it removes security from the way. So the probe declares its own
slice with method security on, and it supplies the bean by the name that every
expression resolves.

Allowed class:

```kotlin
package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.ChatTopicServiceController
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.test.config.TestLongCompositeServiceBeans
import com.demo.chat.test.controller.webflux.LongTypeUtilConfiguration
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import com.demo.chat.test.key.FakeKeyServices
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The REST boundary probe, allowed answer, for `CHAT-znprrzhn`.
 *
 * `GET /topic/list` reaches `listRooms` directly and crosses the proxy.
 * `GET /topic/id/{id}` reaches `restGetRoom`, a default method on
 * `ChatTopicServiceRestMapping`, which calls `getRoom` on its own object.
 * That call is not a route, so it crosses the proxy only if the annotation
 * reaches the controller through the `TopicServiceAccess` supertype.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatTopicServiceController::class,
        RestBoundaryProbeAllowedConfiguration::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic"])
class RestBoundaryProbeAllowedTests {

    @Autowired lateinit var client: WebTestClient

    @Test
    fun `an allowed caller reaches the mapped route`() {
        client.get().uri("/topic/list").exchange()
            .expectStatus().isOk
    }

    /** The facade route. It is refused only if a denial can reach its object. */
    @Test
    fun `an allowed caller reaches the facade route`() {
        client.get().uri("/topic/id/12345").exchange()
            .expectStatus().isOk
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class RestBoundaryProbeAllowedConfiguration {

    @Bean
    @Primary
    fun probeBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java)
        given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))
        given(broker.hasAccessByKeyId(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))
        return broker
    }

    /** **The name matters.** Every expression resolves the bean `chatAccess`. */
    @Bean
    fun chatAccess(broker: AccessBroker<Long>): SpringSecurityAccessBrokerService<Long> =
        SpringSecurityAccessBrokerService(
            broker, FakeKeyServices.longRoots(), FakeKeyServices.longVerifier()
        )

    /** The controller must not be handed a null for a `Flux` route. */
    @Bean
    fun probeRooms(beans: TestLongCompositeServiceBeans<Long, String>): Boolean {
        given(beans.mockTopicBean.listRooms()).willReturn(Flux.empty())
        return true
    }
}
```

Denied class. It is a separate file, and its only difference is the fixed
answer:

```kotlin
package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.ChatTopicServiceController
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.test.config.TestLongCompositeServiceBeans
import com.demo.chat.test.controller.webflux.LongTypeUtilConfiguration
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import com.demo.chat.test.key.FakeKeyServices
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

/**
 * The REST boundary probe, denied answer, for `CHAT-znprrzhn`.
 *
 * **A refusal must arrive, and the service must never run.** An assertion
 * that only reads a status code cannot tell a refusal from a route that was
 * never mapped. The call count is the control.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatTopicServiceController::class,
        RestBoundaryProbeDeniedConfiguration::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic"])
class RestBoundaryProbeDeniedTests {

    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>

    @Test
    fun `a denied caller is refused at the mapped route and listRooms is never called`() {
        client.get().uri("/topic/list").exchange()
            .expectStatus().isForbidden

        verify(beans.topicService(), never()).listRooms()
    }

    /**
     * **The facade route is the finding of this task.** If it answers 200
     * under a denial, the same-object call crosses no proxy, and the
     * annotations must move onto the mapping interface.
     */
    @Test
    fun `a denied caller is refused at the facade route and getRoom is never called`() {
        client.get().uri("/topic/id/12345").exchange()
            .expectStatus().isForbidden

        verify(beans.topicService(), never()).getRoom(anyObject())
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class RestBoundaryProbeDeniedConfiguration {

    @Bean
    @Primary
    fun probeBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java)
        given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))
        given(broker.hasAccessByKeyId(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))
        return broker
    }

    @Bean
    fun chatAccess(broker: AccessBroker<Long>): SpringSecurityAccessBrokerService<Long> =
        SpringSecurityAccessBrokerService(
            broker, FakeKeyServices.longRoots(), FakeKeyServices.longVerifier()
        )
}
```

`TestLongCompositeServiceBeans` holds Mockito mocks, so `verify` on
`beans.topicService()` reads a real mock rather than a plain object.

- [ ] **Step 2: Add the delegation to `ChatTopicServiceController`**

**The annotated interface is a supertype, and it is not a delegation target.**
`beans.topicService()` answers a `ChatTopicService<T, String>`. The annotated
`TopicServiceAccess<T, String>` is a different type, so a delegation clause
cannot bind it. Add one supertype. Keep the existing clause unchanged.

```kotlin
@RestController
@RequestMapping("/topic")
class ChatTopicServiceController<T>(private val beans: CompositeServiceBeans<T, String>) :
    ChatTopicServiceRestMapping<T>,
    TopicServiceAccess<T, String>,
    ChatTopicService<T, String> by beans.topicService()
```

Add the import for `TopicServiceAccess`.
- [ ] **Step 3: Run both probe classes**

Run the allowed class:

```
mvn -o -pl chat-core,chat-webflux -Dtest=RestBoundaryProbeAllowedTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/rest-probe-allowed.log 2>&1; echo "EXIT=$?"; tail -30 /tmp/rest-probe-allowed.log
```

**Expected: both tests PASS.** If either fails, this step is the measurement
of that failure, and Step 4 reads it.

Then run the denied class:

```
mvn -o -pl chat-core,chat-webflux -Dtest=RestBoundaryProbeDeniedTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/rest-probe-denied.log 2>&1; echo "EXIT=$?"; tail -30 /tmp/rest-probe-denied.log
```

**Expected: both tests PASS.** A failure names which route the boundary does
not reach, and Step 4 reads it.

- [ ] **Step 4: Read the result**

The two runs answer two separate questions. Read each one alone.

| Reading | What it proves | Next step |
|---|---|---|
| The allowed class passes and the denied class passes | The boundary holds on the mapped route and on the facade route | Continue |
| The mapped route fails in both classes | `@EnableReactiveMethodSecurity` does not apply inside this slice, so the probe measures its own fixture | Repair the slice, then rerun Step 3 |
| The facade route answers 200 in the denied class | **The finding of this task.** The same-object call crosses no proxy | **STOP.** Return the design to the owner |
| The facade route answers 500 in the denied class | The expression could not be evaluated, and the repair is a Task 3 concern | Run Task 3 first, then rerun |

**The third row stops the plan.** A facade route that ignores a denial means
the annotations must move onto the mapping interface. That is a different
design, and the owner decides it.

- [ ] **Step 5: Commit**

```bash
git add chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatTopicServiceController.kt \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeAllowedTests.kt \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeDeniedTests.kt
git commit -m "Measure the REST dispatch, including a facade route

CHAT-znprrzhn. The facade methods call their own members, so the probe
covers one mapped route and one facade route. Two classes hold one fixed
answer each, so neither answer depends on a system property.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Repair the raw id expressions

`CHAT-eoqkbqve`. Five expressions pass a raw id to the two argument
`hasAccessTo`, whose compiled signature is `hasAccessTo(Key, String)`. SpEL
resolves by name, then by argument count, then by assignability, so a `Long`
fails with `EL1004E` and the route refuses every caller.

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/TopicServiceAccess.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt`
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt`

**Interfaces:**
- Consumes: `SpringSecurityAccessBrokerService.hasAccessToId(target: T, perm: String)`, and the existing `evaluate(expression, variable, value, access)` helper of `SendCheckExpressionTests`
- Produces: the repaired annotation text that Tasks 6 to 8 rely on.

**`SendCheckExpressionTests` already exists** at
`chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt`.
It holds a real `CoreAuthorizationService`, a `RootKeysFixture`, and an
`evaluate` helper that evaluates the real annotation text through SpEL. **This
task extends it and adds no harness.** The keys `CALLER`, `ROOM`, `TOPIC_ROOT`
and `USER_ROOT` already exist there.

- [ ] **Step 1: Write the failing test**

Add a helper that reads the annotation of a named method, and one test per
repaired site.

```kotlin
    /** The annotation text of a method of an access interface. */
    private fun expressionOf(type: Class<*>, name: String, arity: Int = 1): String =
        type.methods
            .first { it.name == name && it.parameterCount == arity }
            .getAnnotation(PreAuthorize::class.java).value

    /**
     * **A raw id must bind.** The two argument `hasAccessTo` compiles to
     * `hasAccessTo(Key, String)`, and SpEL resolves a method by name, then by
     * argument count, then by assignability. A `Long` is not a `Key`, so the
     * call raises `EL1004E` and every caller is refused with no cause.
     */
    @Test
    fun `the room members expression binds a raw id`() {
        val access = access(listOf(grant(CALLER, ROOM, "MEMBERS")))

        assertThat(
            evaluate(
                expressionOf(TopicServiceAccess::class.java, "roomMembers"),
                "req", ByIdRequest(ROOM.id), access
            )
        ).describedAs("the room members expression").isTrue()
    }

    @Test
    fun `the get room expression binds a raw id`() {
        val access = access(listOf(grant(CALLER, ROOM, "GET")))

        assertThat(
            evaluate(
                expressionOf(TopicServiceAccess::class.java, "getRoom"),
                "req", ByIdRequest(ROOM.id), access
            )
        ).describedAs("the get room expression").isTrue()
    }

    @Test
    fun `the message by id expression binds a raw id`() {
        val access = access(listOf(grant(CALLER, ROOM, "GET")))

        assertThat(
            evaluate(
                expressionOf(MessageServiceAccess::class.java, "messageById"),
                "req", ByIdRequest(ROOM.id), access
            )
        ).describedAs("the message by id expression").isTrue()
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl chat-core,chat-security -Dtest=SendCheckExpressionTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/eoq1.log 2>&1; echo "EXIT=$?"; grep -m3 "EL1004E\|SpelEvaluationException" /tmp/eoq1.log`

Expected: FAIL. `EL1004E` names `hasAccessTo` on a target of type `Long`.

- [ ] **Step 3: Repair the expressions**

`TopicServiceAccess.kt` takes four changes:

```kotlin
    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'REM')")
    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'GET')")
    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'MEMBERS')")
    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships>
```

`MessageServiceAccess.kt` takes two:

```kotlin
    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'SUBSCRIBE')")
    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'GET')")
    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>
```

`#req.id` replaces `#req.component1()`. Both read the same value, and `.id` is
the property the data class declares.

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -o -pl chat-core,chat-security -Dtest=SendCheckExpressionTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/eoq2.log 2>&1; echo "EXIT=$?"; tail -12 /tmp/eoq2.log`

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add chat-security/src/main/kotlin/com/demo/chat/security/access/composite/TopicServiceAccess.kt \
        chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt \
        chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt
git commit -m "Repair the access expressions that pass a raw id

CHAT-eoqkbqve. The two argument hasAccessTo compiles to
hasAccessTo(Key, String), so SpEL cannot bind a Long to it and the call
fails with EL1004E. Five sites now use hasAccessToId, which takes a raw id.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Repair the property accessors

`CHAT-eoqkbqve`. `MembershipRequest` declares `uid` and `roomId` as Kotlin
data class properties. Each compiles to `getUid()` and `getRoomId()`. The
expressions name `#req.uid()` and `#req.roomId()`, which are methods Kotlin
never generates.

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/TopicServiceAccess.kt`
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt`

**Interfaces:**
- Consumes: `MembershipRequest<T>(val uid: T, val roomId: T)` from `com.demo.chat.domain`, and `expressionOf` from Task 3
- Produces: the repaired `topic-join` and `topic-leave` expressions.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `the join expression binds both properties of a membership request`() {
        val access = access(listOf(grant(CALLER, ROOM, "JOIN")))

        assertThat(
            evaluate(
                expressionOf(TopicServiceAccess::class.java, "joinRoom"),
                "req", MembershipRequest(CALLER.id, ROOM.id), access
            )
        ).describedAs("the join expression").isTrue()
    }

    @Test
    fun `the leave expression binds both properties of a membership request`() {
        val access = access(listOf(grant(CALLER, ROOM, "JOIN")))

        assertThat(
            evaluate(
                expressionOf(TopicServiceAccess::class.java, "leaveRoom"),
                "req", MembershipRequest(CALLER.id, ROOM.id), access
            )
        ).describedAs("the leave expression").isTrue()
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl chat-core,chat-security -Dtest=SendCheckExpressionTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/eoq3.log 2>&1; echo "EXIT=$?"; grep -m3 "EL1004E\|EL1008E" /tmp/eoq3.log`

Expected: FAIL. `EL1004E` names `uid()` or `EL1008E` names an unknown property.

- [ ] **Step 3: Repair the expressions**

```kotlin
    @PreAuthorize("@chatAccess.hasAccessToId(#req.uid, 'JOIN')")
    override fun joinRoom(req: MembershipRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.roomId, 'JOIN')")
    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void>
```

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -o -pl chat-core,chat-security -Dtest=SendCheckExpressionTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/eoq4.log 2>&1; echo "EXIT=$?"; tail -12 /tmp/eoq4.log`

Expected: PASS, 8 tests, 0 skipped.

- [ ] **Step 5: Commit**

```bash
git add chat-security/src/main/kotlin/com/demo/chat/security/access/composite/TopicServiceAccess.kt \
        chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt
git commit -m "Repair the access expressions that name a Kotlin accessor

CHAT-eoqkbqve. A data class property uid compiles to getUid(), so SpEL
resolves #req.uid and never #req.uid(). Two sites repaired.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Make the room by name route fail closed

`CHAT-eoqkbqve`. **`topic-by-name` cannot be repaired mechanically, so it is
made to deny.**

`TopicServiceAccess.getRoomByName` takes `ByStringRequest`, whose only field is
`name: String`. So there is no room id at the point of the check, and no target
check can be honest.

**The owner decided on 2026-10-01 that the route stays fail-closed.** An
unguarded route would expose room reads once this issue wires the controller,
and no grant could protect it. The route carries an explicit deny expression
instead.

**So the route is unusable until `CHAT-dgjhljbl` lands.** That issue resolves a
name to a room key and restores a target check.

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/TopicServiceAccess.kt`
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt`
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`

**Interfaces:**
- Consumes: nothing. The expression is a SpEL literal.
- Produces: a route that refuses every caller, and a matrix row that says so.

- [ ] **Step 1: Write the failing test**

```kotlin
    /**
     * **`topic-by-name` denies every caller, and the most privileged caller
     * is the control.** `ByStringRequest` holds a name and no id, so there is
     * no target key to check. A wildcard row on every domain must still be
     * refused, or the test cannot tell a denial from a missing grant.
     *
     * The route is unusable until `CHAT-dgjhljbl` resolves a name to a key.
     */
    @Test
    fun `the room by name expression denies a fully privileged caller`() {
        val access = access(
            listOf(
                grant(CALLER, TOPIC_ROOT, "*"),
                grant(CALLER, MESSAGE_ROOT, "*"),
                grant(CALLER, USER_ROOT, "*"),
            )
        )

        assertThat(
            evaluate(
                expressionOf(TopicServiceAccess::class.java, "getRoomByName"),
                "req", ByStringRequest("any-room"), access
            )
        ).describedAs("the room by name expression").isFalse()
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl chat-core,chat-security -Dtest=SendCheckExpressionTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/eoq5.log 2>&1; echo "EXIT=$?"; grep -m3 "EL1004E\|SpelEvaluationException" /tmp/eoq5.log`

Expected: FAIL. The expression calls `hasAccessTo` with a `String`, and no
overload accepts one, so SpEL raises `EL1004E`.

- [ ] **Step 3: Make the route deny**

Replace the expression with a literal `false`. A literal carries no method
resolution and no bean lookup, so it cannot fail open and it cannot fail on an
evaluation error.

```kotlin
    /**
     * **This route denies every caller, and it cannot do otherwise.**
     * `ByStringRequest` holds a name and no id, so there is no target key at
     * the check. The owner decided on 2026-10-01 that the route stays
     * fail-closed: an unguarded route would expose room reads and no grant
     * could protect it.
     *
     * **It is unusable until `CHAT-dgjhljbl` lands.** That issue resolves a
     * name to a room key and restores a target check.
     */
    @PreAuthorize("false")
    override fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>>
```

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -o -pl chat-core,chat-security -Dtest=SendCheckExpressionTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/eoq6.log 2>&1; echo "EXIT=$?"; tail -12 /tmp/eoq6.log`

Expected: PASS, 9 tests, 0 skipped.

- [ ] **Step 5: Update the matrix**

Add this row to `docs/ANONYMOUS-AUTHORIZATION.md`, and mark it unusable.

| Operation | anonymous | authenticated | owner |
|---|---|---|---|
| `getRoomByName`, MessageTopic GET | deny | deny | deny |

State the reason and the follow-up in the row. `CHAT-dgjhljbl` restores a
target check.

- [ ] **Step 6: Commit**

```bash
git add chat-security/src/main/kotlin/com/demo/chat/security/access/composite/TopicServiceAccess.kt \
        chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt \
        docs/ANONYMOUS-AUTHORIZATION.md
git commit -m "Make the room by name route fail closed (CHAT-eoqkbqve)

topic-by-name holds a name and no id, so no target check can be honest.
The owner decided the route stays fail-closed rather than unguarded.
CHAT-dgjhljbl resolves a name to a key and restores the check.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: Wire the remaining RSocket controllers

**Files:**
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt:20-33`

**Interfaces:**
- Consumes: `MessageServiceAccess<T, V>` and `UserServiceAccess<T>`
- Produces: three annotated controller beans, which Tasks 7 and 8 assert on.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `every composite controller carries its access interface`() {
        assertThat(MessageServiceController::class.java.interfaces)
            .describedAs("the message controller")
            .anyMatch { MessageServiceAccess::class.java.isAssignableFrom(it) }

        assertThat(UserServiceController::class.java.interfaces)
            .describedAs("the user controller")
            .anyMatch { UserServiceAccess::class.java.isAssignableFrom(it) }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl chat-core,chat-service-controller -Dtest=RSocketBoundaryProbeTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/wire1.log 2>&1; echo "EXIT=$?"; tail -20 /tmp/wire1.log`

Expected: FAIL. The message and user controllers declare no access interface.

- [ ] **Step 3: Wire the two controllers**

**The annotated interface is a supertype, and it is not a delegation target.**
The delegate answers a plain service type, so the delegation clause cannot bind
the annotated interface. Add one supertype to each controller, and keep the
existing clause.

```kotlin
@ConditionalOnProperty(prefix = "app.controller", name = ["message"])
@Controller
@MessageMapping("message")
class MessageServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    MessageServiceControllerMapping<T, V>,
    MessageServiceAccess<T, V>,
    ChatMessageService<T, V> by b.messageService()

@ConditionalOnProperty(prefix = "app.controller", name = ["user"])
@Controller
@MessageMapping("user")
class UserServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    UserServiceControllerMapping<T>,
    UserServiceAccess<T>,
    ChatUserService<T> by b.userService()
```

`UserServiceController` takes `CompositeServiceBeans<T, V>`. The earlier draft
of this task wrote `CompositeServiceBeans<T>`, which does not match the
interface.

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -o -pl chat-core,chat-service-controller -Dtest=RSocketBoundaryProbeTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/wire2.log 2>&1; echo "EXIT=$?"; tail -12 /tmp/wire2.log`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt \
        chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/RSocketBoundaryProbeTests.kt
git commit -m "Wire the message and user RSocket controllers (CHAT-znprrzhn)

Each controller declares its access interface by delegation, so the
controller bean carries the annotation.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: The RSocket caller state tests

Task 1 answers whether the dispatch crosses the proxy. This task pins the three
caller states that the owner named, and each one names its own outcome.

| State | Identity at the broker |
|---|---|
| An RSocket caller with no credential | the `Anon` root key |
| A call with no security context | none, so denied |
| An authenticated caller without the grant | its own user key, and denied |

**The identity is not the answer.** The mocked broker answers the permission.
This task reads the principal that the broker **received**, so a change to
`ContextIdentity` fails here.

**Files:**
- Modify: `chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/probe/RSocketBoundaryProbeDeniedTests.kt`
- Modify: `chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/probe/RSocketBoundaryProbeAllowedTests.kt`

**Interfaces:**
- Consumes: the two probe classes of Task 1
- Produces: the caller state evidence for the issue

- [ ] **Step 1: Add the two denied states**

Add to `RSocketBoundaryProbeDeniedTests`. Both tests capture the principal.

```kotlin
    /**
     * **A caller with no credential reaches the `Anon` identity.** The RSocket
     * security seam calls `anonymous`, so the context holds an anonymous
     * authentication. The broker must receive a principal, and the permission
     * answer is false.
     */
    @Test
    fun `a caller with no credential reaches the Anon identity`() {
        given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        val principals = ArgumentCaptor.forClass(Mono::class.java)

        StepVerifier.create(
            requester.route("topic-list").retrieveFlux(MessageTopic::class.java)
        ).expectError(AccessDeniedException::class.java)
            .verify(Duration.ofSeconds(10))

        verify(accessBroker).hasAccessByPrincipal(
            principals.capture(), anyObject(), anyObject()
        )

        assertThat(principals.value.block(Duration.ofSeconds(5)))
            .describedAs("the principal the broker received")
            .isNotNull
    }

    /**
     * **A call with no security context reaches no identity.** The call runs on
     * the injected bean, so it crosses no transport and no seam installs a
     * context. `ContextIdentity` answers nothing, and the broker receives an
     * empty principal.
     */
    @Test
    fun `a call with no security context reaches no identity`() {
        val principals = ArgumentCaptor.forClass(Mono::class.java)

        StepVerifier.create(controller.listRooms())
            .expectError(AccessDeniedException::class.java)
            .verify(Duration.ofSeconds(10))

        verify(accessBroker).hasAccessByPrincipal(
            principals.capture(), anyObject(), anyObject()
        )

        assertThat(principals.value.block(Duration.ofSeconds(5)))
            .describedAs("the principal the broker received")
            .isNull()
    }
```

The second test needs the controller bean. Autowire it.

```kotlin
    @Autowired private lateinit var controller: TopicServiceController<Long, String>
```

- [ ] **Step 2: Add the authenticated state**

Add to `RSocketBoundaryProbeAllowedTests`.

```kotlin
    /**
     * **An authenticated caller reaches its own user key.** The credential is
     * the one that `RSocketSecurityTestConfiguration` registers. The broker
     * must receive a principal that is not the `Anon` root key.
     */
    @Test
    fun `an authenticated caller reaches its user identity`() {
        given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))

        val principals = ArgumentCaptor.forClass(Mono::class.java)

        requester.route("topic-list")
            .metadata(UsernamePasswordMetadata("user", "password"), SIMPLE_AUTH)
            .retrieveFlux(MessageTopic::class.java)
            .collectList()
            .block(Duration.ofSeconds(10))

        verify(accessBroker).hasAccessByPrincipal(
            principals.capture(), anyObject(), anyObject()
        )

        assertThat(principals.value.block(Duration.ofSeconds(5)))
            .describedAs("the principal the broker received")
            .isNotNull
    }
```

Add the imports for `ArgumentCaptor` and `UsernamePasswordMetadata`, and for
`com.demo.chat.config.controller.composite.TopicServiceController`.

- [ ] **Step 3: Run both probes**

Run: `mvn -o -pl chat-core,chat-service-controller -Dtest='RSocketBoundaryProbe*Tests' -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/states.log 2>&1; echo "EXIT=$?"; tail -40 /tmp/states.log`

Expected: PASS. The denied class runs 3 tests. The allowed class runs 2.

- [ ] **Step 4: Record what each state answered**

Write the measured identity of each state into `docs/IDENTITY-POLICY.md` beside
the matching row. A state whose principal is empty and a state whose principal
is the `Anon` key are different outcomes, and this task is where that
difference is measured rather than reasoned.

- [ ] **Step 5: Commit**

```bash
git add chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/probe/ \
        docs/IDENTITY-POLICY.md
git commit -m "Pin the three caller states at the RSocket boundary (CHAT-znprrzhn)

An anonymous caller, an absent context, and an authenticated caller are
three outcomes. Each test reads the principal the broker received.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: Wire the remaining webflux controllers

**Files:**
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageServiceController.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatUserServiceController.kt`

**Interfaces:**
- Consumes: `MessageServiceAccess<T, String>` and `UserServiceAccess<T>`
- Produces: the two remaining annotated REST controller beans.

- [ ] **Step 1: Write the failing test**

Add to `RestBoundaryProbeDeniedTests`:

```kotlin
    @Test
    fun `every REST composite controller carries its access interface`() {
        assertThat(ChatMessageServiceController::class.java.interfaces)
            .describedAs("the message controller")
            .anyMatch { MessageServiceAccess::class.java.isAssignableFrom(it) }

        assertThat(ChatUserServiceController::class.java.interfaces)
            .describedAs("the user controller")
            .anyMatch { UserServiceAccess::class.java.isAssignableFrom(it) }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl chat-core,chat-webflux -Dtest=RestBoundaryProbeDeniedTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/wire3.log 2>&1; echo "EXIT=$?"; tail -20 /tmp/wire3.log`

Expected: FAIL.

- [ ] **Step 3: Wire the two controllers**

**The annotated interface is a supertype, and it is not a delegation target.**
Add one supertype to each controller, and keep the existing clause.

```kotlin
class ChatMessageServiceController<T>(private val beans: CompositeServiceBeans<T, String>) :
    ChatMessageServiceRestMapping<T>,
    MessageServiceAccess<T, String>,
    ChatMessageService<T, String> by beans.messageService()
```

```kotlin
class ChatUserServiceController<T>(s: CompositeServiceBeans<T, String>) :
    ChatUserServiceRestMapping<T>,
    UserServiceAccess<T>,
    ChatUserService<T> by s.userService()
```

`ChatUserServiceController` holds its parameter as `s`. Keep that name.

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -o -pl chat-core,chat-webflux -Dtest=RestBoundaryProbeAllowedTests,RestBoundaryProbeDeniedTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/wire4.log 2>&1; echo "EXIT=$?"; tail -12 /tmp/wire4.log`

Expected: PASS.

- [ ] **Step 5: Run the whole webflux suite**

The recall controller and the REST route guards share this context.

Run: `mvn -o -pl chat-core,chat-webflux test > /tmp/webflux-all.log 2>&1; echo "EXIT=$?"; grep -E "^\[INFO\] Tests run:" /tmp/webflux-all.log | tail -3`

Expected: PASS, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageServiceController.kt \
        chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatUserServiceController.kt \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeAllowedTests.kt \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeDeniedTests.kt
git commit -m "Wire the message and user REST controllers (CHAT-znprrzhn)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: Prove the deployment seam

The unit slice proves the proxy. This task proves that a real deployment
registers the same annotated beans and refuses through the whole stack.

**Files:**
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/CompositeAccessEnforcementTests.kt`

**Interfaces:**
- Consumes: `RoomOwnerGrantConfiguration` and `MethodSecurityConfiguration` from `chat-security`
- Produces: the deployment evidence for the issue.

- [ ] **Step 1: Write the test**

Follow `RoomOwnerGrantWiringTests` for the property list. The context needs
`app.service.composite.auth`, `app.controller.*` and `app.users.create=true`.

```kotlin
    @Autowired lateinit var applicationContext: ApplicationContext
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>

    @Test
    fun `the deployment registers a proxied topic controller`() {
        val controller = applicationContext.getBean(TopicServiceController::class.java)

        assertThat(AopUtils.isAopProxy(controller))
            .describedAs("the topic controller proxy")
            .isTrue()
    }

    @Test
    fun `a caller holding no room grant is refused and the room is not created`() {
        val room = composite.topicService()
            .addRoom(ByStringRequest("enforcedroom"))
            .contextWrite(context(someUser()))

        StepVerifier.create(room)
            .verifyError(AccessDeniedException::class.java)

        assertThat(
            stores.topicIndexService().findBy(ByStringRequest("enforcedroom"))
                .collectList().block(Duration.ofSeconds(10))
        ).describedAs("the index rows for the refused room").isEmpty()
    }
```

Fill `someUser()` and `context(...)` from `RoomOwnerGrantWiringTests`, which
already builds both.

- [ ] **Step 2: Run it**

Run: `mvn -o -pl chat-core,chat-deploy-memory -Dtest=CompositeAccessEnforcementTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/deploy1.log 2>&1; echo "EXIT=$?"; tail -25 /tmp/deploy1.log`

Expected: PASS.

**A pass on the first run is a finding about the test.** This task runs after
the wiring, so the test cannot show RED on its own. Prove the RED by removing
the delegation clause from `TopicServiceController` by hand, running the two
tests, reading the failure, and restoring the clause. Record the RED and the
restore in the ledger.

- [ ] **Step 3: Commit**

```bash
git add chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/CompositeAccessEnforcementTests.kt
git commit -m "Prove the enforcement seam in a deployment (CHAT-znprrzhn)

The controller bean is a proxy, and a caller with no grant is refused
before the room is written.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 10: The full gate

**Files:**
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`
- Modify: `docs/BUILD-HEALTH.md`

- [ ] **Step 1: Run the default build**

Run: `./shell-scripts/build-health.sh > /tmp/default.log 2>&1; echo "EXIT=$?"; tail -20 /tmp/default.log`

Expected: exit 0, and the reported counts match `docs/BUILD-HEALTH.md`.

- [ ] **Step 2: Run the CI build**

This builds the image and runs the container tests, which is where the shell
behaviour shows.

Run: `./shell-scripts/build-health.sh --ci > /tmp/ci.log 2>&1; echo "EXIT=$?"; tail -20 /tmp/ci.log`

Expected: exit 0.

**A shell test failure here is the expected signal of decision 1.** The shell
holds no credential, so `addRoom` and `send` deny. That work is
`CHAT-wbcbptiq`, and it lands before this issue. If it has not landed, record
the failure on `CHAT-wbcbptiq` and stop.

**One shell failure here is expected and no credential repairs it.**
`topic-by-name` is fail-closed by Task 5, so every shell call to
`getRoomByName` is refused. Five sites use it: `TopicCommands.kt` lines 55,
62, 77 and 99, and `PubSubCommands.kt` line 46, which is send by topic name.
`CHAT-dgjhljbl` restores that route.

**So the shell suite cannot be green when this issue lands.** Record every
failure that names `getRoomByName` on `CHAT-dgjhljbl`, and separate it from
the credential failures of `CHAT-wbcbptiq`. If those two sets cannot be told
apart, this task is not complete.

- [ ] **Step 3: Update the matrix**

Move every wired row of `docs/ANONYMOUS-AUTHORIZATION.md` from "latent" to
"enforced", and record the date and the commit beside each one.

- [ ] **Step 4: Commit**

```bash
git add docs/ANONYMOUS-AUTHORIZATION.md docs/BUILD-HEALTH.md
git commit -m "Record the enforced matrix (CHAT-znprrzhn)

Every wired row leaves the latent state. The document now states what a
deployment enforces, and not only what the configuration means.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
