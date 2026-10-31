# General register tools

The general commands reside in `shell-scripts/register*`.
They include register configuration, authenticated calls, bundle packing, byte ranges, source splitting, and deterministic UUID generation.
`register-lib.sh` holds shared validation and neutral deployment inputs.
Deployment wrappers retain their deployment isolation policy.

`CHAT_DEPLOYMENT_HOME` selects an existing absolute deployment directory when a command needs installed state.
`CHAT_DEPLOYMENT_SECRETS_DIR` and `CHAT_DEPLOYMENT_SECRETS_FILE` select existing credential inputs.
Credentials remain outside profiles.
`CHAT_REGISTER_TOPIC_NAME` or `CHAT_REGISTER_TOPIC_ID` selects the register.
`CHAT_SHELL_CLI` and `CHAT_MCP_STDIO` can explicitly select the corresponding general executable.
Otherwise the command uses executables in its own tool directory.

Select identity and connection values through `CHAT_NODE_PROFILE` or explicit neutral inputs.
An existing CLI refusal retains its status.
A profile port offset must pass validation before register creation starts its transport.
The temporary compatibility reader accepts legacy selectors during migration.

`register-import pack` performs no network operation.
The existing apply and verify algorithms still require their metadata transport through `REGISTER_IMPORT_CQLSH`.
The general command does not discover that transport through a deployment checkout.
This extraction adds no metadata query and no resident-portal import capability.
The existing first-delivery import owner restriction remains unchanged.

The original deployment entry points remain available during this intermediate state.
They keep their existing implementation until the approved extraction installs final adapters.
General and deployment suites run separately to preserve this boundary.
