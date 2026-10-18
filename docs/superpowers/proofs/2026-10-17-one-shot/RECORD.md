# Stage 0 proof record: chat-shell as a one-shot command

Issue: `CHAT-jmnjvupr`. Plan: `docs/superpowers/plans/2026-10-17-topic-register-stage-0-proof.md`. Design: `docs/superpowers/specs/2026-10-17-topic-register-design.md`.

## Environment

- Proof commit: `d3c3992efa27d3891c07ae595f92661c22e3055a`. The proof scripts were not committed yet, and they reach no module.
- Run on 2026-10-17 from 23:21 to 23:24 PDT, on Darwin 25 with `openjdk version "25" 2025-09-16`.
- The disposable core: `chat-deploy-memory`, node id `0`, key type `uuid`, RSocket on 6990, management on 6991. It started in its own process group, inside the sandbox.
- The shell: `com.demo.chat.ChatAppKt` started with `java` directly. The options came from the `chat-build shell` plan with the default feature set. `spring.shell.interactive.enabled=false`, except in C9.
- Denied ports: 5432, 6379, 6790, 6791, 8500, 8600, 9000, 9001, and 9042. These are all the host ports that the running containers published.

## The isolation proof

Before any shell ran, a Java `SocketChannel` probe ran inside the sandbox against every denied port. All 9 answered `java.net.SocketException: Operation not permitted`, and none connected. After the core started, the same probe connected to 6990 and 6991.

No case hit a denied port. The `docker ps` lines after the run equal the lines before it. Node 16 shows `Up 26 hours` in both.

## Answers

**Q1. Does the shell run one command and then exit? Yes.** With the interactive mode off, `NonInteractiveShellRunner` ran each command, and each process ended by itself. No case reached the 60-second limit. After each case, its process group was empty and 9101 had no listener. Cases C1 to C8 and C10.

**Q2. What do the streams hold besides command output?**
- Standard output holds the banner (7 lines and a `Powered by Spring Boot 4.0.8` line), 8 or more Logback lines at `INFO`, and then the command output. One log line is a Netty DNS warning about `MacOSDnsServerAddressStreamProvider`.
- On a failure, standard output also holds the Spring Boot failure report and the full stack trace. Cases C4 to C8 and C11.
- Standard error holds JVM warnings alone. Every run prints `WARNING: Using incubator modules: jdk.incubator.vector` (55 bytes). A run that opens an RSocket connection also prints 4 `sun.misc.Unsafe` warnings from `rsocket-core-1.1.5` (531 bytes in total).
- Every run starts the reactive web server on port 9101.

**Q3. Which exit statuses?** Exit status 0 means success: C1, C2, C3, C9, and C10. Exit status 1 means every failure: a wrong password (C4), an unknown command (C5), a refusal by the core (C6, C7), the prompt path (C8), and a core that is not available (C11). The status does not tell the failure kinds apart. C4 also prints `Authentication failed :Invalid Credentials` on standard output, from `println`.

**Q4. Does `@<file>` run a script? Yes.** C7 ran the first line, a login that printed `Logged in as Admin`. It then ran the second line, `add-topic`, which failed. The failure stopped the script, so the third line did not run. The exit status was 1.

**Q5. How long does one run take?** The wall time was 6.3 to 7.4 seconds per run, from the launch to the process exit. Spring reported a start in 2.9 to 3.3 seconds. The process time at start was 3.1 to 3.5 seconds. The rest is JVM start, the command, the shutdown, and the launch wrappers.

**Q6. Does `CORE_PORT=6990` route the connection? Yes.** Each run that sent an RSocket request (C2, C3, C4, C6, C7, and C10) had connections to 6990 and 6991 alone. C1, C5, C8, and C9 had a connection to 6991 alone, for the root keys. C11 had no connection, because the core was stopped. The read-back in C10 could not confirm a write, because no write succeeded. See finding F1.

**Q7. Does the current `login` prompt read standard input? No.** C8 sent the password on standard input. The command failed with `java.lang.UnsupportedOperationException: readPassword with prompt not implemented`, and the exit status was 1. So the `InputReader` that `NonInteractiveShellRunner` gives a command does not read standard input. This tests the current password prompt alone. No case ran a command that reads `System.in` directly, so the record does not show whether such a command gets the process standard input.

**Q8. What does the shell do today with redirected input?** C9 ran in interactive mode with `/dev/null` as standard input. The shell logged `Running in interactive mode, arguments will be ignored`. It printed the prompt `chat-shell:anonymous :> ` on standard output, read the end of input, and exited with status 0. `whoami` did not run. This agrees with the owner's report that `chat-shell` cannot run a one-shot command today.

## Findings

- **F1. `add-topic` fails on the core.** C6 (no login) and C7 (after a login as `Admin`) both failed with `ApplicationErrorException (0x201): A key id cannot be a byte[]`. The proof cannot tell whether the interactive shell fails in the same way against a `uuid` core, or whether the one-shot path causes it. `register open` needs `add-topic`, so this blocks stage 3. `CHAT-siaetqtr` holds it.
- **F2. A core that is not available fails the start, not the command.** In C11, the bean `rootKeyStartup` got `Connection refused: /127.0.0.1:6991` during the context start. No command ran. The exit status was 1 after 6.3 seconds.
- **F3. Each run starts a web server on the fixed port 9101.** Two one-shot runs at the same time would collide on that port.

## Effect on stage 1

| Part | Result | What changes |
|------|--------|--------------|
| 1. A silent startup | Changes | The banner, the Logback lines, and the failure report all go to standard output, and all must move. A successful run writes JVM warnings to standard error. The rule "nothing on standard error" needs two more changes: the shell must not load `jdk.incubator.vector`, and the `sun.misc.Unsafe` warnings of `rsocket-core` must stop. If either one cannot change, the rule needs the owner. |
| 2. One output path and one exit status | Changes | Today every failure gives status 1 through the Spring Boot failure report. Stage 1 must map each failure to the table: 77 for a failed login, 2 for an unknown command, 69 for a core that is not available at start (F2), and 1 for a command failure. `login` writes with `println` (C4). |
| 3. Command execution | Holds, with two changes | `NonInteractiveShellRunner` works with `chat-shell`, and the process exits. The `@` refusal is needed (Q4). **One run takes about 6.5 seconds**, so a register read made of several one-shot runs passes the 10-second target of decision 20. The session-start read must run in one process, or use the resident mode. The web server on 9101 must not block a second run (F3). |
| 4. A login with no prompt | Holds | The password prompt gets no input in one-shot mode (Q7), so a login from `CHAT_SHELL_USER` and `CHAT_SHELL_PASSWORD_FILE` is necessary. Each one-shot run is a new process, so each one logs in again. No run has tested a login from a password file. |
| 5. The core address | Holds | `CORE_PORT` and `CORE_MGMT_PORT` routed every connection (Q6). |


## Artifacts

Each project jar on the classpath, compared by SHA-256 with the jar that the build made at the proof commit.

| Classpath | Module | Result |
|-----------|--------|--------|
| shell | `chat-core` | match |
| shell | `chat-security` | match |
| shell | `chat-shell` | match |
| shell | `chat-deploy` | match |
| shell | `shared-deploy-configuration` | match |
| shell | `chat-shell-host` | match |
| shell | `chat-client-rsocket` | match |
| shell | `chat-service-composite` | match |
| core | `chat-index-lucene` | match |
| core | `chat-core` | match |
| core | `chat-vector-simple` | match |
| core | `chat-vector-embedded` | match |
| core | `chat-embedding-openai` | match |
| core | `chat-embedding-local` | match |
| core | `chat-persistence-memory` | match |
| core | `chat-messaging-memory` | match |
| core | `chat-service-composite` | match |
| core | `chat-deploy` | match |
| core | `shared-deploy-configuration` | match |
| core | `chat-service-controller` | match |
| core | `chat-security` | match |

## Results

| ID | Arguments | Standard input | Exit | Wall ms | Out bytes | Err bytes | Timed out | Remote ports |
|----|-----------|----------------|------|---------|-----------|-----------|-----------|--------------|
| C1 | `help` | `/dev/null` | 0 | 6464 | 3192 | 55 | 0 | 6991 |
| C2 | `whoami` | `/dev/null` | 0 | 6801 | 2072 | 531 | 0 | 6990,6991 |
| C3 | `login --username Admin --password <password>` | `/dev/null` | 0 | 6840 | 2014 | 531 | 0 | 6990,6991 |
| C4 | `login --username Admin --password wrong` | `/dev/null` | 1 | 6942 | 15775 | 531 | 0 | 6990,6991 |
| C5 | `no-such-command` | `/dev/null` | 1 | 6468 | 4581 | 55 | 0 | 6991 |
| C6 | `add-topic --name proof-room` | `/dev/null` | 1 | 6995 | 11763 | 531 | 0 | 6990,6991 |
| C7 | `@$S/script.txt` | `/dev/null` | 1 | 7404 | 12004 | 531 | 0 | 6990,6991 |
| C8 | `login --username Admin` | the password and a newline | 1 | 6595 | 5228 | 55 | 0 | 6991 |
| C9 | `whoami` | `/dev/null` | 0 | 6516 | 2154 | 55 | 0 | 6991 |
| C10 | `show-topics` | `/dev/null` | 0 | 6968 | 1997 | 531 | 0 | 6990,6991 |
| C11 | `whoami` | `/dev/null` | 1 | 6267 | 18848 | 55 | 0 | none |

## Stream heads

The first 10 lines of each stream that is not empty. The banner lines appear once, in C1. Trailing blanks are removed from each line, so that `git diff --check` passes. The raw stream files kept them.

**C1, standard output:**

```
   _|_|_|  _|                    _|            _|_|_|  _|                  _|  _|
 _|        _|_|_|      _|_|_|  _|_|_|_|      _|        _|_|_|      _|_|    _|  _|
 _|        _|    _|  _|    _|    _|            _|_|    _|    _|  _|_|_|_|  _|  _|
 _|        _|    _|  _|    _|    _|                _|  _|    _|  _|        _|  _|
   _|_|_|  _|    _|    _|_|_|      _|_|      _|_|_|    _|    _|    _|_|_|  _|  _|



Powered by Spring Boot 4.0.8
23:22:25.429 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 54840 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
```

**C1, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
```

**C2, standard output:**

```
23:22:33.100 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 55037 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:22:33.102 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:22:35.227 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:22:35.475 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:22:35.712 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:22:35.726 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 2.887 seconds (process running for 3.082)
273c45c3-f55e-39e7-9753-ad4312ff4eb6 : Anon | ANY USER | chatimg://anon.png
23:22:35.882 [main] org.springframework.boot.reactor.netty.GracefulShutdown.shutDownGracefully - Commencing graceful shutdown. Waiting for active requests to complete
23:22:35.883 [netty-shutdown] org.springframework.boot.reactor.netty.GracefulShutdown.doShutdown - Graceful shutdown complete
```

**C2, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by io.rsocket.internal.jctools.queues.UnsafeAccess (file:/Users/darkbit1001/.m2/repository/io/rsocket/rsocket-core/1.1.5/rsocket-core-1.1.5.
WARNING: Please consider reporting this to the maintainers of class io.rsocket.internal.jctools.queues.UnsafeAccess
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
```

**C3, standard output:**

```
23:22:41.131 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 55241 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:22:41.133 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:22:43.269 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:22:43.521 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:22:43.764 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:22:43.777 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 2.91 seconds (process running for 3.106)
Logged in as Admin
23:22:43.985 [main] org.springframework.boot.reactor.netty.GracefulShutdown.shutDownGracefully - Commencing graceful shutdown. Waiting for active requests to complete
23:22:43.987 [netty-shutdown] org.springframework.boot.reactor.netty.GracefulShutdown.doShutdown - Graceful shutdown complete
```

**C3, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by io.rsocket.internal.jctools.queues.UnsafeAccess (file:/Users/darkbit1001/.m2/repository/io/rsocket/rsocket-core/1.1.5/rsocket-core-1.1.5.
WARNING: Please consider reporting this to the maintainers of class io.rsocket.internal.jctools.queues.UnsafeAccess
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
```

**C4, standard output:**

```
23:22:49.175 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 55456 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:22:49.178 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:22:51.307 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:22:51.557 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:22:51.800 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:22:51.814 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 2.895 seconds (process running for 3.088)
Authentication failed :Invalid Credentials
23:22:51.987 [main] org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger.logMessage -

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
```

**C4, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by io.rsocket.internal.jctools.queues.UnsafeAccess (file:/Users/darkbit1001/.m2/repository/io/rsocket/rsocket-core/1.1.5/rsocket-core-1.1.5.
WARNING: Please consider reporting this to the maintainers of class io.rsocket.internal.jctools.queues.UnsafeAccess
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
```

**C5, standard output:**

```
23:22:57.335 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 55659 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:22:57.337 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:22:59.464 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:22:59.719 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:22:59.960 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:22:59.973 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 2.899 seconds (process running for 3.092)
23:22:59.978 [main] org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger.logMessage -

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
23:22:59.983 [main] org.springframework.boot.SpringApplication.reportFailure - Application run failed
```

**C5, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
```

**C6, standard output:**

```
23:23:05.062 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 55862 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:23:05.065 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:23:07.296 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:23:07.554 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:23:07.797 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:23:07.810 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 3.02 seconds (process running for 3.224)
23:23:07.879 [main] org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger.logMessage -

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
23:23:07.885 [main] org.springframework.boot.SpringApplication.reportFailure - Application run failed
```

**C6, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by io.rsocket.internal.jctools.queues.UnsafeAccess (file:/Users/darkbit1001/.m2/repository/io/rsocket/rsocket-core/1.1.5/rsocket-core-1.1.5.
WARNING: Please consider reporting this to the maintainers of class io.rsocket.internal.jctools.queues.UnsafeAccess
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
```

**C7, standard output:**

```
23:23:13.328 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 56062 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:23:13.331 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:23:15.755 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:23:16.027 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:23:16.288 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:23:16.302 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 3.261 seconds (process running for 3.483)
Logged in as Admin
23:23:16.609 [main] org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger.logMessage -

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
```

**C7, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by io.rsocket.internal.jctools.queues.UnsafeAccess (file:/Users/darkbit1001/.m2/repository/io/rsocket/rsocket-core/1.1.5/rsocket-core-1.1.5.
WARNING: Please consider reporting this to the maintainers of class io.rsocket.internal.jctools.queues.UnsafeAccess
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
```

**C8, standard output:**

```
23:23:21.926 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 56281 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:23:21.929 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:23:24.166 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:23:24.417 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:23:24.662 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:23:24.675 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 3.022 seconds (process running for 3.233)
23:23:24.683 [main] org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger.logMessage -

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
23:23:24.689 [main] org.springframework.boot.SpringApplication.reportFailure - Application run failed
```

**C8, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
```

**C9, standard output:**

```
23:23:29.732 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 56481 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:23:29.734 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:23:31.959 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:23:32.220 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:23:32.463 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:23:32.475 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 3.014 seconds (process running for 3.22)
23:23:32.477 [main] org.springframework.shell.core.InteractiveShellRunner.run - Running in interactive mode, arguments will be ignored
chat-shell:anonymous :> 23:23:32.482 [main] org.springframework.boot.reactor.netty.GracefulShutdown.shutDownGracefully - Commencing graceful shutdown. Waiting for active requests to complete
23:23:32.484 [netty-shutdown] org.springframework.boot.reactor.netty.GracefulShutdown.doShutdown - Graceful shutdown complete
```

**C9, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
```

**C10, standard output:**

```
23:23:37.452 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 56675 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:23:37.454 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:23:39.715 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:23:39.978 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:23:40.226 [main] org.springframework.boot.reactor.netty.NettyWebServer.start - Netty started on port 9101 (http)
23:23:40.238 [main] com.demo.chat.ChatAppKt.logStarted - Started ChatAppKt in 3.056 seconds (process running for 3.263)

23:23:40.288 [main] org.springframework.boot.reactor.netty.GracefulShutdown.shutDownGracefully - Commencing graceful shutdown. Waiting for active requests to complete
23:23:40.289 [netty-shutdown] org.springframework.boot.reactor.netty.GracefulShutdown.doShutdown - Graceful shutdown complete
```

**C10, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by io.rsocket.internal.jctools.queues.UnsafeAccess (file:/Users/darkbit1001/.m2/repository/io/rsocket/rsocket-core/1.1.5/rsocket-core-1.1.5.
WARNING: Please consider reporting this to the maintainers of class io.rsocket.internal.jctools.queues.UnsafeAccess
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
```

**C11, standard output:**

```
23:23:47.191 [main] com.demo.chat.ChatAppKt.logStarting - Starting ChatAppKt using Java 25 with PID 56896 (/Users/darkbit1001/workspace/demo-chat/.worktrees/chat-jmnjvupr-topic-register/chat-deploy/ta
23:23:47.193 [main] com.demo.chat.ChatAppKt.logStartupProfileInfo - The following 1 profile is active: "shell"
23:23:49.431 [main] org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver.<init> - Exposing 3 endpoints beneath base path '/actuator'
23:23:49.688 [main] io.netty.resolver.dns.DnsServerAddressStreamProviders.<clinit> - Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This m
23:23:51.786 [main] org.springframework.boot.web.server.reactive.context.AnnotationConfigReactiveWebServerApplicationContext.refresh - Exception encountered during context initialization - cancelling
23:23:51.799 [main] org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger.logMessage -

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
23:23:51.809 [main] org.springframework.boot.SpringApplication.reportFailure - Application run failed
org.springframework.context.ApplicationContextException: Failed to start bean 'rootKeyStartup'
```

**C11, standard error:**

```
WARNING: Using incubator modules: jdk.incubator.vector
```
