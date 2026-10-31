# Installed tools

`build-shell-cli-lib` stages shell jars, MCP jars, and `shell-cli.options` without starting a service.
The options headers record key type, node ID, connection ports, Java selection, and source commit.
Select a profile with `CHAT_NODE_PROFILE` or supply explicit identity and port inputs.
`CHAT_BUILD_JAVA` and `CHAT_BUILD_MVN` select the build tools.

`CHAT_SHELL_LIB` selects a shell runtime directory.
An explicitly selected nonempty path requires its options file.
The launcher must not use source execution when that file is absent.
`CHAT_MCP_LIB` selects the MCP jar directory without requiring an installation marker.
An absent directory or empty jar set causes failure.

`CHAT_SHELL_JAVA` and `CHAT_MCP_JAVA` retain their explicit runtime selection.
When no explicit shell Java is selected, an unavailable recorded build-host executable permits Java selection through PATH.
Installed shell options retain key-type and node-ID mismatch checks.
Effective client ports override frozen connection ports, including the root-key URI.
An explicit `ROOTKEY_SOURCE_URI` retains precedence.

The temporary compatibility reader retains old public defaults for earlier installed options and source staging calls.
Those defaults remain in the compatibility component until deployment adapters supply the values.
The current deployment installer copies the profile support libraries with its launchers.
This intermediate state does not establish the final pinned installation contract.

Stubbed tests inspect JVM arguments, environment values, stage files, and failure behavior.
They do not prove real Maven staging or live runtime acceptance.
