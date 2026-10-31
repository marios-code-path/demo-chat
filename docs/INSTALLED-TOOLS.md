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

`chat-tunnel` and `chat-remote` use profile ports and one shared offset calculation.
`CHAT_TUNNEL_AUTHSERV_PORT` supplies the optional authserv forward for `--authserv`.
The profile schema contains no authserv key.
The availability probe returns 0 for free ports, 1 for busy ports, and 2 for a refused check.
Readiness checks establish local listeners only.

The portal tunnel helper resolves its TCP check, tunnel, and CLI from its own tool directory.
`CHAT_TCP_CHECK`, `CHAT_TUNNEL_COMMAND`, and `CHAT_SHELL_CLI` provide explicit executable overrides.
An explicit missing CLI causes refusal before the source portal can execute.
MCP installers accept explicit `CHAT_DEPLOYMENT_HOME`, `CHAT_DEPLOYMENT_BIN`, `CHAT_REGISTER_COMMAND`, and `CHAT_MCP_STDIO` inputs.
They do not discover an installed home or sibling checkout.
Their default register and launcher come from their own tool directory.
An absent default deployment launcher permits the general source launcher.
An explicit launcher never receives that fallback.

`tool-interface.version` declares interface version 1.
`tool-files.json` lists source dependencies and installed file targets.
The source inventory includes command support, Compose inputs, schema inputs, and reactor POM files.
The exact Git pin covers compiled source and resources used by Maven.
The runtime inventory includes the temporary compatibility reader during this intermediate state.
It is not the final neutral release inventory.

`check-tool-source LOCK` requires an explicit absolute `DEMO_CHAT_HOME` at a Git root.
The lock contains `demoChatCommit` plus the inventory fields from `tool-files.json`.
The commit must contain exactly 40 hexadecimal characters.
The checker requires the matching commit, interface version, inventory, and tracked regular support files.
It rejects symlinks, traversal, duplicate targets, tracked changes, tracked bytecode, and non-ignored untracked inputs.
Only repository `.gitignore` rules can exclude untracked inputs from this check.
A tracked repository `.gitignore` must ignore `shell-scripts/__pycache__/probe.pyc`.
Local and global excludes cannot satisfy that precondition.
The probe creates no file and changes no Git index.
The checker returns 2 on refusal and changes no installed state.
A successful intermediate check does not authorize a final deployment release pin.
