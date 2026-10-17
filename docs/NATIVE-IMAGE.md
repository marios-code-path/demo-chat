# GraalVM native image

What the `native` profile does today, what it produces, and why the binary does
not start a full context yet. First measured on 2026-09-17 under
`CHAT-hvactuvm`. Measured again on 2026-10-16 under `CHAT-arcqfjuc`.

## State

| | |
|---|---|
| Builds an image | yes, both modules |
| AOT initializer found | **yes**, since `CHAT-arcqfjuc` moved `main` to the top level |
| Context starts | **no**. AOT fixes the bean set at build time. See below. |

Read the section `Build time composition` before you plan work on this path.
The image is not a usable artifact yet.

## The two modules

| Module | Profile | Image |
|--------|---------|-------|
| `chat-deploy` | `native` | `chat-deploy/target/chat-deploy` |
| `chat-authorization-server` | `native` | `chat-authorization-server/target/chat-authorization-server` |

## The command

```bash
mvn -B -Pnative,expose-rsocket -pl chat-deploy -am package -Dmaven.test.skip=true
mvn -B -Pnative -pl chat-authorization-server -am package -Dmaven.test.skip=true
```

One command per module. The image goal runs in the `package` phase, so no
second command is needed.

`-am` is required. A scoped run without it resolves the upstream modules from
`~/.m2`, and a stale entry there gives a failure that names the wrong cause.

## Measured on 2026-09-17

Machine: GraalVM CE 25+37.1, arm64, 10 cores, 28.45 GiB given to the image
build.

| Module | Image time | Total | Size |
|--------|-----------|-------|------|
| `chat-deploy` | 1 min 12 s | 1 min 21 s | 110.7 MiB |
| `chat-authorization-server` | 1 min 21 s | 2 min 47 s | 126.8 MiB |

Measured again on 2026-10-16, GraalVM CE 25+37.1, Spring Boot 4.0.8, and
`native-maven-plugin` 0.11.5:

| Module | Image time | Total | Size |
|--------|-----------|-------|------|
| `chat-deploy` | 1 min 26 s | 1 min 41 s | 143.5 MiB |
| `chat-authorization-server` | 1 min 22 s | 3 min 8 s | 117.5 MiB |

The `chat-authorization-server` run with `-am` also builds the `chat-deploy`
image, because the module depends on `chat-deploy`. The total includes both
images. `native-maven-plugin` 0.11.5 was absent from `~/.m2`, so the first
run needed the network. An offline run fails until the plugin is present.

The analysis phase reports 18 s with the AOT output present and 14 s without
it. The image is 110.7 MiB with the AOT output and 54.4 MiB without. **A small
image is the symptom of a skipped AOT step, not a saving.**

## The entry point (closed)

Until `CHAT-arcqfjuc`, both binaries stopped at startup with this message:

```
Startup with AOT mode enabled failed: AOT initializer
com.demo.chat.ChatApp$Companion__ApplicationContextInitializer could not be found
```

Cause. `ChatApp` kept `main` in a Kotlin companion object.
`SpringApplication` reads the first stack frame that holds a method named
`main`. That frame was `ChatApp$Companion.main`, because the `@JvmStatic`
bridge on `ChatApp` called it. AOT generates for the class that the build
names, which was `ChatApp`. The two names did not meet.

Repair. Both `ChatApp.kt` files now hold a top level `main`. The entry class
is `com.demo.chat.ChatAppKt`. Every pom site that named the entry class names
`ChatAppKt` now. `ChatApp` stays the `@SpringBootApplication` class, and tests
still name `ChatApp::class`. `EntryPointShapeTests` in `chat-deploy` and
`chat-authorization-server` fails if a companion `main` comes back.

Two other routes were measured on 2026-09-17, and both are closed:

1. **Name the companion as the AOT main class.** `ChatApp$Companion.main` is
   an instance method, and the AOT processor calls it with no receiver. The
   build fails with `NullPointerException: Cannot invoke "Object.getClass()"
   because "o" is null`.
2. **Turn AOT mode off at runtime.** `AotDetector` always reports generated
   artifacts inside a native image, so `spring.aot.enabled` changes nothing
   there.

## Build time composition (open)

Measured on 2026-10-16. Both binaries log `Starting AOT-processed ChatAppKt`,
and then the context fails.

Spring AOT evaluates `@ConditionalOnProperty` and the other conditions at
build time. The image holds only the beans that the AOT run selected. A
property at launch cannot add a bean that the build left out.

This conflicts with decision 1 of the capability design: one classpath with
every provider, and selection at launch. A native image can carry one
composition only, and the build must name it.

**The owner decided on 2026-10-16.** Launch time selection stays the rule for
the JVM images. A native image is one named, fixed composition, and its build
names that composition with properties at AOT time. The first native image is
the authorization server. `CHAT-iudsqbuj` holds the decision and the plan.

Spring profiles are fixed at AOT time too. The authorization server selects
beans with `@Profile("memory")`, `@Profile("jdbc")`,
`@Profile("client-kv-store")`, and `@Profile("client-init")`. So its native
build must name the profiles of the deployed launch.

Observed failures:

| Image | AOT properties | Launch properties | First failure |
|-------|----------------|-------------------|---------------|
| `chat-deploy` | none | none | no `TypeUtil` bean. `app.key.type` was unset at build time. |
| `chat-deploy` | `app.key.type=long`, `app.nodeid=1` | the same, and `spring.application.name` | no `KeyServiceBeans` bean |
| `chat-authorization-server` | none | none | `app.oauth2.jwk.path` is unset |

`chat-deploy` is a base module and holds no store provider. No `KeyServiceBeans`
provider is on its classpath, so it is not a full composition. The deploy
modules, such as `chat-deploy-memory`, add the providers. None of them has a
native profile.

The authorization server image reached Tomcat. It then stopped on the missing
signing key. A full start also needs Postgres. That start was not tried.

To pass properties to the AOT run, use
`-Dspring-boot.aot.jvmArguments="-Dapp.key.type=long ..."`.

## What the profile needed

Three defects were repaired to reach a built image. Each one is recorded in a
comment beside the element that repairs it.

1. **No execution binding.** `mvn -Pnative package` reported SUCCESS and wrote
   no binary. The parent profile runs AOT and collects reachability metadata.
   Nothing invoked the image goal.
2. **No main class.** The plugin reads no main class from a plain jar, and the
   native profile does not repackage. The build stopped with "Please specify
   class containing the main entry point method".
3. **AOT was skipped.** The root pom sets `<skip>true</skip>` on
   `spring-boot-maven-plugin` at plugin level, which stops every goal of that
   plugin, `process-aot` included. The native profile now turns the skip off for
   that one execution. Repackage keeps the skip.

## Two notes

- **The Vector API flag reaches the image.** The build prints `WARNING: Using
  incubator modules: jdk.incubator.vector`, and it suggests
  `-H:+VectorAPISupport` to optimise those operations. No measurement supports
  adding that flag yet.
- **The sandbox self-attach trap of PR #52 was not exercised.** A native build
  skips the tests, so the Byte Buddy agent never loads. `CHAT-bojgvcba` closed
  that risk for the two test modes on Java 25.
