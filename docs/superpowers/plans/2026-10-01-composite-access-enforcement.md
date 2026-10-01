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

Five inputs the spec implies and no task's tests exercise by default. Each
line carries the test that pins it, in the task named beside it.

1. **A caller holding a domain wildcard on a write.** `{User, MessageTopic,
   ALL}` does not cover `SEND`, because `ALL` is a literal. A reasonable person
   expects a wildcard row to cover its own domain. Pinned by `a wildcard row on
   the domain does not cover send` in Task 7.
2. **An id that the registry does not hold.** `hasAccessToId` resolves through
   the registry and denies an unknown id, with no error. Pinned by `an
   unregistered id is refused and no downstream bean is called` in Task 7.
3. **`getRoomByName` has no target key.** The request carries a name and no id,
   so no target check can be honest. The route denies every caller, and it
   never reads the topic. Pinned by `the room by name expression denies a fully
   privileged caller` in Task 5 and by `room by name is refused and the topic
   is never read` in Task 7.
4. **The REST facade methods call the annotated methods on the same object.**
   `restGetRoom`, `restDeleteRoom`, `joinRestRoom`, `leaveRestRoom` and
   `restRoomMembers` are declared on the mapping interface and call its own
   members. Pinned by `a denied caller is refused at the facade route and the
   service is never called` in Task 2.
5. **A streaming route.** `listenTopic` and `listRooms` return a `Flux`. A
   refusal must arrive before any element does. Pinned by `list rooms refuses
   before the first element` in Task 7.

---

### Task 1: The RSocket boundary probe

The gate. One route, one allowed caller, one denied caller. If the allowed
caller is refused, or the denied caller reaches the downstream service, the
boundary is wrong and the work stops here.

**Files:**
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt:29-31`
- Create: `chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/RSocketBoundaryProbeTests.kt`

**Interfaces:**
- Consumes: `TopicServiceAccess<T, V>` from `com.demo.chat.security.access.composite`
- Produces: nothing. This task answers a question.

- [ ] **Step 1: Write the probe**

Shared imports for every snippet in this plan:
`com.demo.chat.test.anyObject` (`(T) -> T` for Mockito),
`reactor.test.StepVerifier`, `org.assertj.core.api.Assertions.assertThat`,
`org.springframework.security.access.AccessDeniedException`,
`reactor.core.publisher.Mono`, and the request types of
`com.demo.chat.domain` (`ByIdRequest`, `ByStringRequest`, `MembershipRequest`,
`MessageSendRequest`).

The route is `topic-list`. Its expression is
`hasAccessToDomain('MessageTopic', 'ALL')`, which is already evaluable and
already allows per `docs/ANONYMOUS-AUTHORIZATION.md`.

```kotlin
package com.demo.chat.test.rsocket

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.MessageTopic
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import reactor.core.publisher.Flux
import java.time.Duration

/**
 * The boundary probe for `CHAT-znprrzhn`. It answers one question: does an
 * RSocket `@MessageMapping` dispatch cross the method security proxy?
 *
 * `topic-list` carries `hasAccessToDomain('MessageTopic', 'ALL')`. That
 * expression is evaluable today and the shipped grants allow it. So an
 * allowed caller must reach the service, and a denied caller must not.
 *
 * **A refusal alone proves nothing.** An unevaluable expression also refuses.
 * The allowed caller is the control, and the zero downstream assertion is
 * what separates a refusal from a broken route.
 */
    @Test
    fun `an allowed caller reaches the service through the mapped route`() {
        val route = requester.route("topic-list")
        val hits = route.retrieveFlux(MessageTopic::class.java)
            .collectList()
            .block(Duration.ofSeconds(10))

        assertThat(hits).describedAs("the route answered").isNotNull
    }

    @Test
    fun `a denied caller is refused and the service is never called`() {
        val broker = beans.topicService()

        val answer = requester.route("topic-list")
            .retrieveFlux(MessageTopic::class.java)
            .collectList()
            .onErrorReturn(emptyList())
            .block(Duration.ofSeconds(10))

        assertThat(answer).describedAs("the route refused").isEmpty()
        verify(broker, never()).listRooms()
    }
}
```

The probe needs a `chatAccess` bean whose answer the test controls.
`RSocketSecurityTestConfiguration` already enables reactive method security
and already supplies a `KeyVerifier` and `RootKeys`. So the probe adds one
configuration beside it, in the same package, and reuses those beans.

```kotlin
@TestConfiguration
class RSocketBoundaryProbeConfiguration {

    /**
     * The answer is a system property, because one test class drives both
     * states and Spring caches the context. The tests read the property in
     * `@BeforeAll`, so the bean is not built twice.
     */
    @Bean
    @Primary
    fun probeBroker(): AccessBroker<Long> = mock(AccessBroker::class.java)

    @Bean
    fun probeChatAccess(
        broker: AccessBroker<Long>,
        rootKeys: RootKeys<Long>,
        verifier: KeyVerifier<Long>,
    ): SpringSecurityAccessBrokerService<Long> {
        val allow = java.lang.Boolean.getBoolean("probe.allow")
        given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(allow))
        return SpringSecurityAccessBrokerService(broker, rootKeys, verifier)
    }
}
```

`RSocketTestBase` declares `requester` itself, so the probe class must not
declare a second one. It autowires `CompositeServiceBeans` instead.

```kotlin
class RSocketBoundaryProbeTests : RSocketTestBase() {

    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
```

Two properties drive the two runs. Add `@TestPropertySource` to the probe, and
run the class twice with `-Dprobe.allow=true` and `-Dprobe.allow=false`.

- [ ] **Step 2: Add the delegation to `TopicServiceController`**

Change line 30 to 32 of `CompositeControllersConfiguration.kt` only. Add the
import for `TopicServiceAccess`.

```kotlin
@ConditionalOnProperty(prefix = "app.controller", name = ["topic"])
@Controller
@MessageMapping("topic")
class TopicServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    TopicServiceControllerMapping<T, V>,
    TopicServiceAccess<T, V> by b.topicService()
```

- [ ] **Step 3: Run the probe with the allowed answer**

Run: `mvn -o -pl chat-core,chat-service-controller -Dtest=RSocketBoundaryProbeTests -Dprobe.allow=true -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/probe-allow.log 2>&1; echo "EXIT=$?"; tail -30 /tmp/probe-allow.log`

Expected: both tests PASS. The allowed caller receives a list, and the denied
caller is refused with no call to `listRooms`.

- [ ] **Step 4: Read the result**

- **The allowed caller is refused.** The proxy does not see the annotation
  through the mapping interface. **STOP.** Record the finding on
  `CHAT-znprrzhn` and return the design to the owner.
- **The denied caller reaches the service.** The dispatcher holds the raw
  delegate and not the proxy. **STOP.** Same route.
- **Both pass.** The boundary holds. Continue to Task 2.

- [ ] **Step 5: Commit**

```bash
git add chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt \
        chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/RSocketBoundaryProbeTests.kt
git commit -m "Measure the RSocket dispatch against the method security proxy

The boundary probe for CHAT-znprrzhn. One mapped route crosses the proxy
and one does not, and each answer carries its own control.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: The REST boundary probe

The second gate. The webflux mapping interface declares five facade methods
that call its own members, so a route can cross the proxy twice or not at all.

**Files:**
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatTopicServiceController.kt:10-11`
- Create: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeTests.kt`

**Interfaces:**
- Consumes: `TopicServiceAccess<T, String>` from `com.demo.chat.security.access.composite`
- Produces: nothing. This task answers a question.

- [ ] **Step 1: Write the probe**

`WebFluxTestConfiguration` builds a permit-all chain and enables no method
security. The probe declares its own slice with method security on.

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
 * The REST boundary probe for `CHAT-znprrzhn`.
 *
 * `GET /topic/id/{id}` reaches `restGetRoom`, which is a default method on
 * `ChatTopicServiceRestMapping`. It calls `getRoom` on its own object. That
 * call is not a route, so it crosses the proxy only if the annotation is
 * inherited from `TopicServiceAccess`.
 *
 * `GET /topic/list` reaches `listRooms` directly.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatTopicServiceController::class,
        RestBoundaryProbeConfiguration::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic"])
class RestBoundaryProbeTests {

    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>

    @Test
    fun `an allowed caller reaches listRooms through the mapped route`() {
        client.get().uri("/topic/list").exchange()
            .expectStatus().isOk
    }

    /** The facade route. The annotation must be inherited from the access interface. */
    @Test
    fun `a denied caller is refused at the facade route and the service is never called`() {
        client.get().uri("/topic/id/12345").exchange()
            .expectStatus().isForbidden

        verify(beans.topicService(), never()).getRoom(anyObject())
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class RestBoundaryProbeConfiguration {

    @Bean
    @Primary
    fun probeBroker(): AccessBroker<Long> = mock(AccessBroker::class.java)

    @Bean
    fun probeChatAccess(broker: AccessBroker<Long>): SpringSecurityAccessBrokerService<Long> {
        val allow = System.getProperty("probe.allow") == "true"
        given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(allow))
        return SpringSecurityAccessBrokerService(broker, FakeKeyServices.longRoots(), FakeKeyServices.longVerifier())
    }
}
```

- [ ] **Step 2: Add the delegation to `ChatTopicServiceController`**

```kotlin
@RestController
@RequestMapping("/topic")
class ChatTopicServiceController<T>(private val beans: CompositeServiceBeans<T, String>) :
    ChatTopicServiceRestMapping<T>,
    TopicServiceAccess<T, String> by beans.topicService()
```

- [ ] **Step 3: Run the probe with the allowed answer**

Run: `mvn -o -pl chat-core,chat-webflux -Dtest=RestBoundaryProbeTests -Dprobe.allow=true -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/rest-probe.log 2>&1; echo "EXIT=$?"; tail -30 /tmp/rest-probe.log`

Expected: both tests PASS.

- [ ] **Step 4: Read the result**

- **The facade route reaches the service under a denial.** The same-object call
  crosses no proxy. **STOP** and return the design to the owner. The repair
  would move the annotations onto the mapping interface, and that is a
  different design.
- **Both pass.** The boundary holds on both transports. Continue.

- [ ] **Step 5: Commit**

```bash
git add chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatTopicServiceController.kt \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeTests.kt
git commit -m "Measure the REST dispatch, including a facade route

CHAT-znprrzhn. The facade methods call their own members, so the probe
covers one mapped route and one facade route.

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

```kotlin
@ConditionalOnProperty(prefix = "app.controller", name = ["message"])
@Controller
@MessageMapping("message")
class MessageServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    MessageServiceControllerMapping<T, V>,
    MessageServiceAccess<T, V> by b.messageService()

@ConditionalOnProperty(prefix = "app.controller", name = ["user"])
@Controller
@MessageMapping("user")
class UserServiceController<T>(b: CompositeServiceBeans<T>) :
    UserServiceControllerMapping<T>,
    UserServiceAccess<T> by b.userService()
```

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

**Files:**
- Modify: `chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/RSocketBoundaryProbeTests.kt`

**Interfaces:**
- Consumes: the three wired controllers from Tasks 1 and 6
- Produces: the boundary evidence for the whole issue.

- [ ] **Step 1: Write the caller state tests**

```kotlin
    @Test
    fun `a caller with no credential reaches the Anon identity`() {
        val route = requester.route("topic-list")
        val answer = route.retrieveFlux(MessageTopic::class.java)
            .collectList()
            .block(Duration.ofSeconds(10))

        assertThat(answer).describedAs("the anonymous route answered").isNotNull
    }

    @Test
    fun `a call with no security context reaches no identity`() {
        StepVerifier.create(beans.topicService().listRooms())
            .verifyError(AccessDeniedException::class.java)
    }

    @Test
    fun `a wildcard row on the domain does not cover send`() {
        val route = requester.route("message-send")
            .data(MessageSendRequest("body", 1L, 7L))

        StepVerifier.create(route.retrieveMono(Key::class.java))
            .verifyError(AccessDeniedException::class.java)
    }

    @Test
    fun `an unregistered id is refused and no downstream bean is called`() {
        val route = requester.route("topic-by-id").data(ByIdRequest(999999L))

        StepVerifier.create(route.retrieveMono(MessageTopic::class.java))
            .verifyError(AccessDeniedException::class.java)

        verify(beans.topicService(), never()).getRoom(anyObject())
    }

    @Test
    fun `list rooms refuses before the first element`() {
        val route = requester.route("topic-list")

        StepVerifier.create(route.retrieveFlux(MessageTopic::class.java))
            .expectError(AccessDeniedException::class.java)
            .verify(Duration.ofSeconds(10))
    }

    /**
     * **The fail-closed route of Task 5 refuses before the topic is read.**
     * `topic-by-name` carries an explicit deny, so the refusal must arrive
     * with no lookup at all. A test that only asserts the refusal cannot tell
     * a refusal from a broken route.
     */
    @Test
    fun `room by name is refused and the topic is never read`() {
        val route = requester.route("topic-by-name").data(ByStringRequest("enforcedroom"))

        StepVerifier.create(route.retrieveMono(MessageTopic::class.java))
            .expectError(AccessDeniedException::class.java)
            .verify(Duration.ofSeconds(10))

        verify(beans.topicService(), never()).getRoomByName(anyObject())
    }
```

- [ ] **Step 2: Run them with the denied answer**

Run: `mvn -o -pl chat-core,chat-service-controller -Dtest=RSocketBoundaryProbeTests -Dprobe.allow=false -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/state1.log 2>&1; echo "EXIT=$?"; tail -30 /tmp/state1.log`

Expected: PASS. Every denial carries a zero downstream assertion.

- [ ] **Step 3: Commit**

```bash
git add chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/RSocketBoundaryProbeTests.kt
git commit -m "Pin the three caller states at the RSocket boundary (CHAT-znprrzhn)

An anonymous caller, an absent context, and an authenticated caller
without the grant are three outcomes. Each test names its own.

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

Add to `RestBoundaryProbeTests`:

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

Run: `mvn -o -pl chat-core,chat-webflux -Dtest=RestBoundaryProbeTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/wire3.log 2>&1; echo "EXIT=$?"; tail -20 /tmp/wire3.log`

Expected: FAIL.

- [ ] **Step 3: Wire the two controllers**

```kotlin
class ChatMessageServiceController<T>(private val beans: CompositeServiceBeans<T, String>) :
    ChatMessageServiceRestMapping<T>,
    MessageServiceAccess<T, String> by beans.messageService()
```

```kotlin
class ChatUserServiceController<T>(private val beans: CompositeServiceBeans<T, String>) :
    ChatUserServiceRestMapping<T>,
    UserServiceAccess<T> by beans.userService()
```

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -o -pl chat-core,chat-webflux -Dtest=RestBoundaryProbeTests -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/wire4.log 2>&1; echo "EXIT=$?"; tail -12 /tmp/wire4.log`

Expected: PASS.

- [ ] **Step 5: Run the whole webflux suite**

The recall controller and the REST route guards share this context.

Run: `mvn -o -pl chat-core,chat-webflux test > /tmp/webflux-all.log 2>&1; echo "EXIT=$?"; grep -E "^\[INFO\] Tests run:" /tmp/webflux-all.log | tail -3`

Expected: PASS, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageServiceController.kt \
        chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatUserServiceController.kt \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/RestBoundaryProbeTests.kt
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
`getRoomByName` is refused. Four sites use it: `TopicCommands.kt` lines 55,
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
