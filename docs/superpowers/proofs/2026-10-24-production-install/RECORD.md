# The first production install of node 9

Date: 2026-10-24 UTC. Issue: `CHAT-faqxupam`. Related: `CHAT-bjqzvsoe`,
`CHAT-acsvenkb`, `CHAT-sufkkqon`, `CHAT-ggxrzzrg`.

This record holds the first install of node 9 from master, with the live
proofs of that installed home. The owner asked for one deployable from a
master that passes its checks.

## The source

| Item | Value |
|---|---|
| Master commit | `c7421b4d45257d980cb0584d292b3e7568491d55` |
| Merge of | PR #274, two parents |
| Earlier merges of this chain | PR #269, PR #271, PR #272, PR #273 |

Master carried two failing checks before this commit, and neither came from a
product change.

- The node 9 gate expected status `2` from an installed `register` with no
  shell CLI. The install ships `bin/chat-shell-cli` since `CHAT-bjqzvsoe`, so
  the wrapper stops at the login with status `77`.
- `McpAdapterHarnessTests` pinned client `1.31.0`, and the dependabot commit
  `46fc6da1` of 2026-10-23 moved the package files to `1.32.0`. Maven CI failed
  on master from that commit, on `c6d21e86`, `1d23a180`, and `fe9fa19b`.

PR #274 fixed both. Each fix failed first.

## The state of the master checks

The `build` job of Maven CI failed on `c6d21e86`, `1d23a180`, and `fe9fa19b`,
and its reason was the pin: `expected: <1.31.0> but was: <1.32.0>` in
`McpAdapterHarnessTests`. That job passes on `c7421b4d`.

The `integration` job of `c7421b4d` fails for another reason. The log reads:

```
> Pulling run image 'docker.io/paketobuildpacks/ubuntu-noble-run-tiny:0.0.150' for platform 'linux/arm64'
> Running creator
   [creator]     exec /cnb/lifecycle/creator: exec format error
Failed to execute goal org.springframework.boot:spring-boot-maven-plugin:4.0.8:build-image (default) on project chat-deploy-memory-integration-tests
```

The runner of that job is `ubuntu-latest`, which is `linux/amd64`. So the
builder pulled an image of another architecture, and the lifecycle binary
cannot run. No file of this repository names a platform, and the earlier runs
hold no such error, so the cause sits above this repository. The builder image
of the module is `paketobuildpacks/builder-noble-java-tiny:latest`, and a
moving tag can change under a build.

So master does not pass its checks yet. This install came from the commit, and
not from a passing run. The owner asked for a deployable from a green master,
and this record names the gap.

## The install

Command: `bash node-9/install`, from the master checkout, with the default
tools.

| Item | Value |
|---|---|
| Marker `installed-at` | `2026-10-24T00:52:23Z` |
| Marker `source-commit` | `c7421b4d45257d980cb0584d292b3e7568491d55` |
| Marker `cli` | `lib, java 25` |
| Jar set | 149 jars, 65896207 bytes |
| `lib/` entries | 150, which is the jar set and `shell-cli.options` |
| Frozen options | `# key-type uuid`, `# node-id 9`, `# java /usr/bin/java` |
| Temporary stages left | none |

The installed `node-9/bin/register-import` has the same SHA-256 as the file of
master, so the home holds master's code and not a branch copy.

## The proofs

Every command below ran against the installed home at `/opt/demo-chat/node-9`.

| Check | Result |
|---|---|
| `chat-shell-cli whoami`, login Theta | status `0`, identity `Theta`, 0 bytes on standard error |
| `register read --as Theta` | status `0`, 14 records, 0 bytes on standard error |
| The same read with `HOME=/nonexistent` and no Maven on `PATH` | status `0`, 14 records |

The 14 records are 7 index pointers and 7 topic entries. So the register of
`CHAT-acsvenkb` answers from this home.

Theta is a development identity of this proof. No installed file holds a login
name.

## The offline gate

`bash shell-scripts/test-node-9.sh` gave 49 passes and 11 failures. Every one
of the 11 names a busy node 9 port, or a start step that the port guard
stopped. Node 9 was running, and its three ports were in use.

The two cases that PR #274 repaired both pass here:
`installed_register_needs_a_cli` and `installed_register_needs_a_login`.

A clean full run of that gate needs a stop window of node 9. The owner owns
that window.

## The core restart of this date

Node 9 stopped during an earlier review, and its core did not return. The
measured cause is `java.net.UnknownHostException: node2.lan: Name or service
not known`, thrown while the core created `embeddedVectorCollection`. So the
embedding host is a start dependency of the core. `CHAT-ggxrzzrg` holds that
finding and its options.

The name resolved again at 00:40 UTC, to `192.168.4.81`, and the endpoint
answered HTTP `200`. One `docker start` of the stopped core container reached
health in 10 s. The three other containers stayed up, so no data moved.

`node-9/bin/start` refuses this case with status `3`, because its own Consul
container holds port 6892. The port guard does not know the containers of its
own project.

## Open items

1. A clean full run of the offline gate needs a stop window.
2. `CHAT-ggxrzzrg`: the core cannot start when the embedding host does not
   resolve.
3. `CHAT-sufkkqon`: the member contract is split. The importer reads the
   members from `secrets.env`, and three scripts keep a fixed list.
4. The live re-proof of the member change needs a snapshot of its own date.
   The snapshot of 2026-10-23 UTC expired at midnight UTC.
5. The port guard of `start` refuses a restart while its own project holds a
   port.
