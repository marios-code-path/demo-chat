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

`jvmOptions` holds the options that the image build receives.
With `--bake`, the builder removes `JAVA_TOOL_OPTIONS` from the Maven environment.
The image then receives no resolved option, and `jvmOptions` is `[]`.
`--composition-json --bake` prints that same record.
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

The builder replaces credential values with `<redacted>` by three rules.
Each rule replaces the value and keeps the rest of the option.
Thus a changed credential under these rules does not change the record.

1. A `-D` option is a credential option when the last segment of its key ends with
   `pass`, `password`, `secret`, `token`, `credential`, `credentials`, `header`, or `headers`.
   An index such as `[0]` after `header` or `headers` also matches.
   The rule replaces the whole value.
   For example, `-Dspring.security.user.password=actuator` becomes
   `-Dspring.security.user.password=<redacted>`.
2. The user information of a URI is replaced.
   For example, `http://admin:s3cret@core:6791/rootkeys` becomes `http://<redacted>@core:6791/rootkeys`.
3. A URI parameter after `?`, `&`, or `;` is replaced when its name matches the name rule of rule 1.
   For example, `?token=t0ken&x=1` becomes `?token=<redacted>&x=1`.

All rules ignore case.
These rules do not detect every credential form.
A credential in another form enters the record, for example a bare value under an unrelated key name.
Do not put credentials in build inputs that reach the JVM options.

## Canonical form

The output is canonical UTF-8 JSON followed by one LF.
The canonical form has sorted object keys and compact separators `,` and `:`.
It writes non-ASCII characters directly, as Python `ensure_ascii=False` does.

The digest input is the canonical form without the final LF.
The digest is the SHA-256 of those UTF-8 bytes.

`shell-scripts/fixtures/image-composition/expected.json` holds the record of the example command.

## Reserved image label

An image build sets the label `com.demo.chat.composition.sha256`.
Its value is the lowercase hexadecimal digest of the build record.
The builder computes the digest from the same record that `--composition-json` prints.
The image build and the record read one resolved option list.

The builder passes labels to Maven in `CHAT_IMAGE_COMPOSITION_LABELS`.
That variable activates the `image-composition-labels` profile of the root POM.
The profile sets `BP_IMAGE_LABELS` in the image environment of the `deploy` profile.
The entry merges beside the `BPE_APPEND_JAVA_TOOL_OPTIONS`, `BPE_DELIM_JAVA_TOOL_OPTIONS`, and `BP_JVM_VERSION` entries.
The Paketo image-labels buildpack reads `BP_IMAGE_LABELS` and applies the labels.
A Maven build without the variable does not activate the profile.

A caller can add unrelated labels through `CHAT_IMAGE_COMPOSITION_LABELS`.
The value uses space-separated `key=value` words with shell quotes.
The builder puts the reserved label first and keeps the caller labels in order.
The builder refuses these caller values with status 2, before Maven runs:

- A label with the reserved key.
- A word that is not `key=value`.
- A value that the shell quote rules cannot parse.

A run with `--run` gets no label input.
The builder removes an ambient `CHAT_IMAGE_COMPOSITION_LABELS` from the Maven environment of a run.

The Paketo documentation does not prove that a builder contains the image-labels buildpack.
The real image acceptance of the plan must establish that fact for the selected builder.

## Image manifest fields

`--image-manifest PATH` writes a record after a successful build and image inspection.
Before it writes, the builder reads the reserved label from the inspected image.
A missing label or a different label gives status 1 and no record.
A successful record adds two fields:

| Field | Value |
| --- | --- |
| `compositionVersion` | Integer `1`. |
| `compositionSha256` | The digest that the reserved label holds. |

## Verify an image

Two general tools verify an image. Each has no deployment defaults.
Each proves a different fact, so neither replaces the other.
A deployment builder runs both.

### Composition

```bash
image_id=$(shell-scripts/verify-image-composition --expected expected.json --image REFERENCE)
```

The verifier checks that the expected file has the exact version 1 schema.
It recomputes the digest with the canonical form above, through `jq -cS`.
It then inspects `REFERENCE` once.
The reserved label of that image must equal the digest.
On success, it prints the immutable image ID of that inspection on standard output.
A later check must use that ID, because a tag can move to another image.

| Status | Meaning |
| --- | --- |
| 0 | The label equals the expected digest. |
| 1 | The label is missing or different, the inspection gives no immutable ID, or Docker fails. |
| 2 | The input is invalid, or a command is missing. |

A Docker failure always gives status 1, whatever status Docker returns.
The verifier refuses these expected files with status 2, before it inspects the image:

- A file that is not readable, not UTF-8, or not JSON.
- A record without the exact version 1 fields and types.
- A version other than the integer `1`, for example `2` or `1.0`.
- A string with a control character or DEL.
  `jq` and Python write these characters in different ways.

### Source commit

```bash
shell-scripts/verify-image-source --boot-jar FILE --commit COMMIT --image "$image_id"
```

The verifier reads `git.commit.id` from these stamps:

- `BOOT-INF/classes/git.properties` of the boot jar.
- `git.properties` of each `BOOT-INF/lib/chat-*.jar` in the boot jar.
- `/workspace/BOOT-INF/classes/git.properties` in the image layers.

Each commit must equal `COMMIT`, a full hexadecimal commit ID.
The image must be an immutable image ID, `sha256:` and 64 hexadecimal digits.
`docker create` makes a container record and starts no process.
The verifier removes each record that it created, and no other record.

| Status | Meaning |
| --- | --- |
| 0 | Every stamp equals the commit. |
| 1 | A stamp is missing or different, or a Docker step fails. |
| 2 | The input is invalid, or a command is missing. |

A source stamp proves the commit alone.
Two images from one commit can hold different compositions.
Thus a passed source check never replaces the composition check.
A missing composition label fails the composition check. It does not fall back to the source check.

A failed `docker create` that still prints a container ID leaves a record.
The verifier removes that record too.

`shell-scripts/build-node-9-image` still holds its own copy of the source checks.

## Real image acceptance

*Measured on 2026-10-31 at commit `9f956ff0`, in a linked worktree.*

The image build used builder `docker.io/paketobuildpacks/builder-noble-java-tiny:0.0.200`.
It used the flags of the example command and the tag `chat-proof-gyfhbsbd/cassandra-core-service-rsocket:0.0.1`.
The build started no service and changed no live deployment.

- Image ID: `sha256:14028437a082fd8c6075ea8c3ecd9ed777e9db154da3a4c44468d66e7eafa54c`, arm64, no repository digest.
- Buildpacks: ca-certificates 3.14.3, bellsoft-liberica 11.10.1, syft 2.43.3,
  executable-jar 6.17.4, dist-zip 5.14.3, spring-boot 5.40.3,
  environment-variables 4.12.0, image-labels 4.14.4.
- Thus this builder contains the image-labels buildpack, and the buildpack applied the reserved label.
- Label: `com.demo.chat.composition.sha256=6304f6b30f0943c5f16ad8d7fb9e9e80718c279538bb5f42f380d64169bd21bf`.
  That value equals the digest of `fixtures/image-composition/expected.json`.
- The image manifest took a record with `compositionVersion` 1 and the same digest.
- `verify-image-composition` with that record gave status 0 and the image ID above.
- `verify-image-composition` with the node ID 8 record gave status 1. The source commit did not change.
- `verify-image-source` against commit `9f956ff0` gave status 1. The boot jar holds commit `1a8fd033`.
- `verify-image-source` against commit `1a8fd033` gave status 0 for 14 jars and the image layer. It left no container record.

The stamp mismatch is a defect of the stamp in a linked worktree.
git-commit-id-maven-plugin recorded the HEAD of the main checkout, branch `master` at `1a8fd033`.
The worktree HEAD was `9f956ff0`. The source verifier refused the image correctly.
Extraction Task B2 connects both verifiers to the destination node-9 builder.

## Tests

`shell-scripts/test-image-composition.sh` tests the mode.
It replaces Maven, Docker, `curl`, and `wget` with commands that record a call and fail.
It clears every ambient input that the mode reads before it sets its own inputs.
`shell-scripts/test-flags.sh` tests the parser refusals of the mode.
The same composition suite replaces Maven with a stub that records the label input of a build.
`shell-scripts/test-image-manifest.sh` tests the label check of the manifest.
`shell-scripts/test-verify-image-composition.sh` and `shell-scripts/test-verify-image-source.sh` test the verifiers with a Docker stub.
The composition suite holds an image with a correct source stamp and a different composition label.
It also moves the tag to another image after the first inspection.
