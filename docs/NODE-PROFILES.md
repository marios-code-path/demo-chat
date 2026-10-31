# Node profiles

Set `CHAT_NODE_PROFILE` to an absolute path to a readable, non-secret profile file.
The loader reads one literal `KEY=value` record per line.
It never sources or evaluates the file.
It ignores blank lines and lines beginning with `#`.
Each profile requires `NODE_PROFILE_VERSION=1`.

The schema permits deployment identity, service ports, image references, component versions, vector ownership, and non-secret embedding settings.
The complete key list is in `shell-scripts/node-profile.sh`.
Credentials must remain outside the profile.
An unknown key requires a newer compatible tool release.

Run `shell-scripts/node-profile --require NODE_ID` to emit validated records.
The command writes diagnostics to standard error.
Profile errors return status 2 before any records are emitted.
This status rule does not change existing builder or subprocess statuses.

Bash callers can source `shell-scripts/node-profile.sh`.
The function `chat_node_profile_load` accepts required key names as arguments.
It exports effective values only after the complete profile passes validation.
Explicit neutral environment values override file values.
An explicitly empty required value causes failure.

Node IDs use the existing range from 0 through 1023.
Key types are `long` and `uuid`.
Service ports must be distinct integers from 1 through 65535.
Profile selection never searches the current directory or a sibling repository.
