# Generic image composition proof implementation plan

For the implementer: use `superpowers:executing-plans` after owner plan approval.
Use one agent. Do not use subagents or Markdown task checkboxes.

Goal: reject an image built with a different composition even when its source commit matches.
Architecture: the general builder records a digest of its resolved composition in the image.
The general verifier compares that digest with the caller's expected composition and retains the existing source-stamp proof.
Node-9 supplies its expected composition through its single composition definition.
Technology: existing Python builder, Bash verification, JSON, SHA-256, Maven, and Paketo buildpacks.

Status: proposed plan. No implementation is authorized.
Review issue: CHAT-gyfhbsbd. Implementation owner: CHAT-scbvcozr.
Prerequisite: reviewed foundation implementation under CHAT-qdsmackk.
The repository extraction plan consumes the reviewed result before its final release pin.

## Scope and limits

The owner must approve this plan before implementation.
This plan adds no deployment-specific constants to demo-chat.
The proof covers the composition that the trusted builder resolves and records.
It is not a signature, remote attestation, or proof against a malicious image producer.
Source stamps remain a separate check.
Missing composition metadata must fail the proof. It must not fall back to source stamps alone.
Credentials must not enter composition records, labels, diagnostics, or test artifacts.

## Task C1: Expose the resolved composition without running a build

Modify `shell-scripts/chat-build`.
Create `shell-scripts/test-image-composition.sh` and `shell-scripts/fixtures/image-composition/expected.json`.
Extend `shell-scripts/test-flags.sh` only where the new output mode affects the parser.
Document the format in `docs/IMAGE-VERIFICATION.md`.

Add `--composition-json` as an output mode after existing validation and feature resolution.
It must perform no Maven, Docker, network, or filesystem mutation.
The mode returns canonical UTF-8 JSON followed by one LF.
Use sorted object keys, compact separators, and array order from the resolved launch plan.
Use UTF-8 characters directly, equivalent to Python `ensure_ascii=False`.
All schema values are strings or string arrays except the integer version.
The digest input omits the final display LF.

The version 1 record has these fields:

| Field | Value |
| --- | --- |
| `version` | Integer `1`. |
| `service` | Resolved service name. |
| `module` | Resolved module basename. |
| `mavenProfiles` | Ordered resolved profile names. |
| `springProfiles` | Ordered resolved Spring profile names. |
| `jvmOptions` | Ordered resolved non-secret JVM configuration from the build context. |

Do not hash raw CLI text or a caller-supplied digest.
Equivalent accepted flag forms must produce the same resolved record.
Different effective backend, index, pubsub, key type, node ID, vector, embedding, or discovery values must change the record.
Include effective topology properties already present in the resolved JVM options.
Do not include credentials from the ambient environment.
Do not add environment dumps to diagnostics.

Add the two-composition case before changing the builder:

```bash
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
shell-scripts/chat-build core --cassandra --index lucene --pubsub redis-pubsub \
    --notls --node-id 7 --key-type uuid --consul --composition-json > "$work/a.json"
shell-scripts/chat-build core --cassandra --index lucene --pubsub redis-pubsub \
    --notls --node-id 8 --key-type uuid --consul --composition-json > "$work/b.json"
test "$(jq -r '.version' "$work/a.json")" = 1
test "$(jq -r '.service' "$work/a.json")" = core
! cmp -s "$work/a.json" "$work/b.json"
```

Run `bash shell-scripts/test-image-composition.sh`.
Expected initial failure: the parser refuses the new mode or both resolved records fail their schema assertions.
Add repeated-output, equivalent-flags, each changed component, profile-input, and no-external-command cases.
Stub Maven and Docker with commands that fail if invoked.
Retain all existing input refusal statuses from the approved profile contract.

## Task C2: Bind the builder's actual composition digest to the image

Modify `shell-scripts/chat-build`, root `pom.xml`, and `shell-scripts/test-image-manifest.sh`.
Extend `shell-scripts/test-image-composition.sh` with fake Maven environment capture.

Before a real image build, compute SHA-256 from the exact canonical record used in Task C1.
Set a reserved label `com.demo.chat.composition.sha256` from that computed digest.
Do not accept an ambient value as the authoritative digest.
Reject a caller label that attempts to redefine the reserved key.
Preserve unrelated caller labels.

Use a Maven profile activated only when `env.CHAT_IMAGE_COMPOSITION_LABELS` exists.
Merge this environment entry into the existing image plugin configuration:

```xml
<image>
    <env>
        <BP_IMAGE_LABELS>${env.CHAT_IMAGE_COMPOSITION_LABELS}</BP_IMAGE_LABELS>
    </env>
</image>
```

The builder sets that environment variable to the generated reserved label plus preserved unrelated labels.
Keep the existing BPE append and delimiter configuration unchanged.
Unrelated direct Maven builds must not acquire an unresolved label property.
The existing image manifest adds `compositionVersion` and `compositionSha256` after successful image inspection.
Read the image label to confirm it matches the computed record before writing a successful manifest entry.

Paketo documents arbitrary labels through `BP_IMAGE_LABELS` in its [configuration guide](https://paketo.io/docs/howto/configuration/#applying-custom-labels).
The [Image Labels buildpack](https://github.com/paketo-buildpacks/image-labels) defines its participation and parsing rules.
This documentation does not prove that the selected local builder contains the buildpack.
The real image acceptance below must establish that fact for the tested builder.

Run `bash shell-scripts/test-image-composition.sh` after adding the fake Maven capture case.
Expected initial failure: Maven receives no generated reserved label or a forged ambient value survives.
Then run `bash shell-scripts/test-image-manifest.sh`.
Expected result after implementation: failed, missing-label, and mismatched-label builds add no success record.
Run `mvn -B -ntp -pl chat-deploy-cassandra help:effective-pom` with the profile activated in a controlled fixture environment.
Inspect the effective image environment to verify that the new entry merges without losing the existing BPE values.

## Task C3: Verify expected composition and preserve source checks

Create demo-chat `shell-scripts/verify-image-source` and `shell-scripts/test-verify-image-source.sh` first.
Extract existing boot-jar, nested-jar, image-layer, source-commit, and owned-container cleanup checks from `shell-scripts/build-node-9-image`.
The source verifier accepts explicit boot-jar, expected source-commit, and immutable image-ID inputs.
It contains no deployment defaults.
Add its non-deployment fixtures before extracting the implementation.
Run `bash shell-scripts/test-verify-image-source.sh`.
Expected initial failure: no general verifier can perform these checks without the deployment builder.
After extraction, stale stamps, absent stamps, failed copies, and cleanup assertions must pass.

Then create demo-chat `shell-scripts/verify-image-composition` and `shell-scripts/test-verify-image-composition.sh`.
The verifier takes `--expected FILE --image REFERENCE`.
It validates the exact version 1 schema and recomputes the expected digest with Task C1 canonicalization.
It inspects the image once and retains its immutable image ID for subsequent checks.
It compares the reserved image label with the expected digest.
Missing metadata or a different digest returns 1.
Invalid verifier input returns 2.
Do not translate an underlying build status 1 into status 2.
The deployment builder invokes both composition verification and source verification.
Neither successful check substitutes for the other.

Add a Docker inspection fixture with the correct source stamp but a different composition label.
Run `bash shell-scripts/test-verify-image-composition.sh`.
Expected initial failure: source-only verification incorrectly accepts that fixture or no composition comparison exists.
Add matching, missing, malformed, unsupported-version, Docker-failure, and retagging fixture cases.
The retagging case must keep subsequent checks bound to the initially inspected image ID.
Retain the nested-jar stamp checks from the source verifier.

The extraction implementer integrates both verifiers into destination `bin/build-node-9-image` under extraction Task B2.
Generate expected JSON from its shared composition definition.
Build the image through the actual selected composition.
Alter the builder's fixture composition while keeping the expected definition and source commit fixed.
The deployment test must fail on composition mismatch.
It must not pass merely because all jar commits match.
Those deployment integration cases belong to extraction Task B2, not this plan's generic implementation gate.

## Real image acceptance and review

After focused suites pass, perform one reviewed local image build with the selected builder.
Record its digest and buildpack inventory.
Inspect the actual image label and run both verifiers against that image ID.
Create a different expected composition without changing the source commit.
Require composition verification to fail against that expectation.
Do not start a service or alter a live deployment for this acceptance.
If Docker or the builder is unavailable, record that limit and retain real image acceptance as incomplete.
Do not add an untested buildpack replacement to bypass a missing label.

Run affected flag and image-manifest suites, `git diff --check`, and `drift check`.
Run full PR CI before claiming merge readiness.
Request implementation review under CHAT-gyfhbsbd and CHAT-scbvcozr.
The final extraction release pin must include the reviewed proof implementation.
