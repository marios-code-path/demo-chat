# Portable register import: live proof

Date: 2026-10-23 UTC. Issue: `CHAT-jmnjvupr`. Child issue: `CHAT-acsvenkb`.
Plan: `docs/superpowers/plans/2026-10-21-portable-register-import.md`, Task 10.

## Basis and authority

The reviewed snapshot names `git:c6d21e866896fb4f2c340264c8ea4b40d55ac741` as its source basis.
That commit remained `master` and `origin/master` before apply.
FP listed six in-progress issues. GitHub listed no open PRs, and PR #271 was the last merge.
Mario confirmed the snapshot, mapping, and content-manifest hashes before the live import.
Mario authorized the live install, apply, and verify.

| Artifact | SHA-256 |
|---|---|
| Snapshot source | `1cdb7db14f7aaa02a9573c639601be4a018e4af65edbcad921b94dc2108d9118` |
| Mapping | `a6b3d8a3766b32011de57787a77d5895f968996c7283142148ee160eb46b8ea3` |
| Content manifest and bundle | `b93791b77a8d46147f71fe73635b602c8f9a1d69df4c010fc91e72af39ca2084` |
| Host receipt file | `912cfd6fe3a288a7faaba32de644c1ffeddb090ea2632cf2b56384169225f80c` |

Mario independently packed the source and mapping. His content-manifest hash matched this bundle.
The bundled `source.md` matched the snapshot by `cmp`.
The source and mapping both name 2026-10-23 UTC.

The installed importer ended at source commit `34ec997204f8790d6eef32f1059fadbad5d9b314`.
Its installed script SHA-256 matched that commit's script at install time.
The installer kept the existing shell CLI jar set. Its marker reports `cli: lib, kept`.
The fixes are on local branch `chat-acsvenkb-whoami-preflight`. They are not merged.

## Live sequence

Node 9 used ports 6890 and 6891. Health returned HTTP `200` before the final apply.
The owner credential passed `whoami` as Mario before any import post.
Mario allowed a stop of the existing node-9 Compose project. The project stopped and started through its installed scripts.
No `chat-deploy core` command ran.

| Step | Status | Result |
|---|---:|---|
| First apply preflight | `2` | The importer expected JSON from `whoami`. No register write occurred. |
| Fresh-index apply | `1` | The importer read an absent index before it created the room. No register write occurred. |
| CQL environment apply | `1` | The importer had not loaded `node-9.env`. No register write occurred. |
| Index creation apply | `1` | `add-topic` created `register-index`. Cassandra index-table lookup found no room ID. No import message posted. |
| Persistence-table apply | `0` | The importer posted the bundle and wrote 29 receipts. |
| First verify | `1` | Sigma's read reported `denied-index`. The importer had not joined readers to the index. |
| Same-bundle apply retry | `0` | The importer joined readers and reused the 29 request IDs. The receipt count stayed 29. |
| Final verify | `0` | Source, entries, pointers, and all four agent reads passed. The read median was 4,894 ms. |

The final `0` statuses match the spec's completed-command result.
The first `2` identified invalid shell CLI output.
Each `1` identified a failed read or incomplete membership.
No partial import was reported as success.

The offline bootstrap suite failed for each new regression before its fix.
After the final fix, all 34 bootstrap cases passed.
`bash -n`, `git diff --check`, and `drift check` passed for the code and spec changes.
The test results cover the importer and its stub. The live results above cover the installed runtime.

## Host state and byte proof

The index room ID is `02d3f900-cf22-11f1-9f49-7184040233df`.
The private index proof names Mario as owner.
The proof used `chat_uuid.chat_room` and `chat_uuid.auth_metadata`.
The Cassandra index tables were empty because node 9 uses Lucene indexes.
The ownership query found one `*` permission for Mario.

Final verify reconstructed 5,845 source bytes from eight ordered parts.
It compared the reconstructed source with the bundle by `cmp` and SHA-256.
It checked six mapped entry ranges and seven index pointers.
The host holds 29 receipts with 29 distinct request IDs and 29 distinct message IDs.
The request ID set equals the bundle manifest's 29 request IDs.

| Message group | IDs |
|---|---|
| Snapshot header | `a4d05370-cf22-11f1-9f49-7184040233df` |
| Snapshot parts 0 to 3 | `a73a9cb0-cf22-11f1-9f49-7184040233df`, `a95f7880-cf22-11f1-9f49-7184040233df`, `ab7d2860-cf22-11f1-9f49-7184040233df`, `ad9cfb20-cf22-11f1-9f49-7184040233df` |
| Snapshot parts 4 to 7 | `afc97810-cf22-11f1-9f49-7184040233df`, `b2164e40-cf22-11f1-9f49-7184040233df`, `b4571680-cf22-11f1-9f49-7184040233df`, `b6862b80-cf22-11f1-9f49-7184040233df` |
| Session range | `b8a89650-cf22-11f1-9f49-7184040233df` |
| Snapshot pointer | `4f68cfb0-cf23-11f1-9f49-7184040233df` |

| Issue | Header ID | Entry ID | Pointer ID |
|---|---|---|---|
| `CHAT-jmnjvupr` | `c835c200-cf22-11f1-9f49-7184040233df` | `ca804e40-cf22-11f1-9f49-7184040233df` | `3cce6400-cf23-11f1-9f49-7184040233df` |
| `CHAT-acsvenkb` | `d9e3f8f0-cf22-11f1-9f49-7184040233df` | `dc2cfe90-cf22-11f1-9f49-7184040233df` | `3fc312f0-cf23-11f1-9f49-7184040233df` |
| `CHAT-owiksxvz` | `ec28f1a0-cf22-11f1-9f49-7184040233df` | `ee5dd300-cf22-11f1-9f49-7184040233df` | `429fcd10-cf23-11f1-9f49-7184040233df` |
| `CHAT-ivtnvgyg` | `fe354e20-cf22-11f1-9f49-7184040233df` | `007f3e20-cf23-11f1-9f49-7184040233df` | `464ad3b0-cf23-11f1-9f49-7184040233df` |
| `CHAT-uxlstrdy` | `1048d690-cf23-11f1-9f49-7184040233df` | `1317d510-cf23-11f1-9f49-7184040233df` | `49672ee0-cf23-11f1-9f49-7184040233df` |
| `CHAT-pphwooqm` | `286ad110-cf23-11f1-9f49-7184040233df` | `2b626630-cf23-11f1-9f49-7184040233df` | `4c85acf0-cf23-11f1-9f49-7184040233df` |

## Read-time gate

Installed `verify` timed five complete session reads. Its median was 4,894 ms, below the 10,000 ms gate.
Five separate read-only checks gave the following results.
They did not copy register text into this record.

| Read | User | Status | Elapsed ms | Records | Denied warnings | Stderr bytes |
|---:|---|---:|---:|---:|---:|---:|
| 1 | Sigma | 0 | 4,853 | 14 | 0 | 0 |
| 2 | Theta | 0 | 4,851 | 14 | 0 | 0 |
| 3 | Phi | 0 | 4,904 | 14 | 0 | 0 |
| 4 | Mario | 0 | 4,860 | 14 | 0 | 0 |
| 5 | Sigma | 0 | 4,852 | 14 | 0 | 0 |

The separate median was 4,853 ms. Every read returned 14 records and no denied warning.
The live run did not test statuses `69`, `75`, `77`, or `124`.
The offline suite covered those failure paths where its fixtures apply.

## Boundary

This record contains no password or register text.
It proves the active snapshot import on this installed node-9 home.
It does not prove the later seven-file archive import or file removal.
Mario reviews this record before Stage 4B starts.
