# GraalVM native image

What the `native` profile does today, what it produces, and the one defect that
stops the binary from starting. Measured on 2026-09-17 under `CHAT-hvactuvm`.

## State

| | |
|---|---|
| Builds an image | yes, both modules |
| Image starts | **no**, both modules fail at startup |
| Blocker | the Kotlin companion object holds `main`. See below. |

Read the blocker section before you plan work on this path. The image is not a
usable artifact yet.

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

The analysis phase reports 18 s with the AOT output present and 14 s without
it. The image is 110.7 MiB with the AOT output and 54.4 MiB without. **A small
image is the symptom of a skipped AOT step, not a saving.**

## The blocker

Both binaries stop at startup with the same message:

```
Startup with AOT mode enabled failed: AOT initializer
com.demo.chat.ChatApp$Companion__ApplicationContextInitializer could not be found
```

Cause. `ChatApp` keeps `main` in a Kotlin companion object:

```kotlin
class ChatApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) { ... }
    }
}
```

`SpringApplication` reads the first stack frame that holds a method named
`main`. That frame is `ChatApp$Companion.main`, because the `@JvmStatic` bridge
on `ChatApp` calls it. So the runtime asks for the initializer of
`ChatApp$Companion`. AOT generates for the class that the build names, which is
`ChatApp`, so it writes `ChatApp__ApplicationContextInitializer`. The two names
do not meet.

Three routes were measured, and two are closed:

1. **Name the companion as the AOT main class.** Closed. `ChatApp$Companion.main`
   is an instance method, and the AOT processor calls it with no receiver. The
   build fails with `NullPointerException: Cannot invoke "Object.getClass()"
   because "o" is null`.
2. **Turn AOT mode off at runtime.** Closed. `AotDetector` always reports
   generated artifacts inside a native image, so `spring.aot.enabled` changes
   nothing there.
3. **Move `main` out of the companion object.** Open. This is the only route
   left. It changes the entry point of every deployment, so it is an owner
   decision. Ten pom sites name the entry class, the image module of the shell
   integration tests among them.

This defect is invisible on the JVM. AOT mode is off there, so the deduced name
is never used.

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
