# Image verification

This document describes the composition record of an image build.
A verifier uses the record to reject an image that a different composition built.
The record is not a signature or a remote attestation.
It does not protect against a malicious image producer.
Source stamps are a separate check.

Work record: fp issue CHAT-gyfhbsbd.
Plan: `docs/superpowers/plans/2026-10-30-image-composition-proof.md`.

## Print the record

`chat-build SERVICE ... --composition-json` prints the record and exits.
The record states the image build of the same flags.
Thus the `--build` refusals apply, with the same exit statuses.

```bash
CONSUL_HOST=10.0.0.5 shell-scripts/chat-build core --cassandra --index lucene \
    --pubsub redis-pubsub --notls --node-id 7 --key-type uuid --consul --composition-json
```

The mode runs no Maven, Docker, or network operation.
It writes no file. It ignores `--env-file` and `--image-manifest`.

The mode refuses these inputs with status 2:

- `--build`, `--run`, or `--agent-json` in the same command.
- `--dry-run` in the same command.
- Consul discovery with no `CONSUL_HOST` and no `--registry`.
  A build finds the host through Docker. This mode does not run Docker.

## Record format, version 1

The record is one JSON object with exactly these fields:

| Field | Value |
| --- | --- |
| `version` | Integer `1`. |
| `service` | Resolved service name. |
| `module` | Resolved module basename. |
| `mavenProfiles` | Ordered resolved Maven profile names. |
| `springProfiles` | Ordered resolved Spring profile names. |
| `jvmOptions` | Ordered resolved `JAVA_TOOL_OPTIONS` entries of the build, with credential values replaced. |

All values are strings or string arrays, except the integer version.
The array order is the order of the resolved launch plan.

The record holds resolved values. It never holds raw command text or a caller-supplied digest.
Thus equivalent flag forms give one record.
For example, `--uuid` and `--key-type uuid` give the same record.
`--node-id 007` and `--node-id 7` give the same record.

A change to these effective values changes the record:
node ID, backend, index, pubsub, key type, vector, embedding, and discovery.
Topology values in the JVM options also change it, for example the Consul host and the core port.
These values can come from the environment or from a node profile.

## Credentials

The record contains no credential.
A `-D` option is a credential option when the last segment of its key ends with
`pass`, `password`, `secret`, `token`, `credential`, `credentials`, `header`, or `headers`.
An index such as `[0]` after `header` or `headers` also matches.
The match ignores case.
The record keeps that option and replaces its value with `<redacted>`.
Thus a changed credential does not change the record.
For example, `-Dspring.security.user.password=actuator` becomes
`-Dspring.security.user.password=<redacted>`.

## Canonical form

The output is canonical UTF-8 JSON followed by one LF.
The canonical form has sorted object keys and compact separators `,` and `:`.
It writes non-ASCII characters directly, as Python `ensure_ascii=False` does.

The digest input is the canonical form without the final LF.
The digest is the SHA-256 of those UTF-8 bytes.

`shell-scripts/fixtures/image-composition/expected.json` holds the record of the example command.

## Tests

`shell-scripts/test-image-composition.sh` tests the mode.
It replaces Maven, Docker, `curl`, and `wget` with commands that record a call and fail.
It clears every ambient input that the mode reads before it sets its own inputs.
`shell-scripts/test-flags.sh` tests the parser refusals of the mode.
