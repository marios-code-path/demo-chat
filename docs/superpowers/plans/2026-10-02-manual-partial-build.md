# Manual partial build plan

Issue: `CHAT-zjoceuib`.

The user approved a manual workflow with module and test-mode inputs.
The workflow and local commands will use one Bash script under `shell-scripts`.
The existing `justfile` will provide short commands.

## Contract

- Require a comma-separated list of module directories from the root POM.
- Accept `unit` and `integration` modes. Default to `unit`.
- Use `clean verify -pl MODULES -am` to create required test JARs in the same Maven reactor.
- Enable the `integration` profile only in integration mode.
- Include the image module and `test-build` profile for shell integration tests.
- Reject invalid inputs before Maven starts. Return Maven's exit status.
- Provide a dry run. Run from the repository root, regardless of the caller's directory.
- Keep existing automatic workflows and build-health commands unchanged.

## Implementation sequence

1. Add command checks for validation, module selection, image inclusion, working directory, and failure propagation.
2. Confirm that the checks fail before the script exists.
3. Add `shell-scripts/build-partial.sh` with the contract above.
4. Add `.github/workflows/maven-partial.yml` with manual inputs and Java 25.
5. Pass workflow inputs through environment variables and quoted shell arguments.
6. Upload the Maven log and test reports even if the build fails.
7. Add local build, preview, and script-check recipes to `justfile`.
8. Document commands and the default-branch requirement for manual dispatch.
9. Run command checks, syntax checks, workflow validation, and a focused Maven build.
10. Check the cumulative diff and drift bindings.

## Evidence limits

Local checks do not prove a GitHub runner result.
GitHub cannot dispatch this workflow until the default branch contains it.
