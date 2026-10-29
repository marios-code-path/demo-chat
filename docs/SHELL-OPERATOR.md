# Chat-Shell one-off operations

Per Theta:

The command

CORE_PORT=6890 ROOTKEY_SOURCE_URI=http://127.0.0.1:6891 \
  node-9/bin/node-9-tunnel node2.lan 0 non-persistent \
  just launch-shell 1 --key-type uuid

Verified with --dry-run:

node-9-tunnel: ssh -N ... -L 127.0.0.1:6890:127.0.0.1:6890 -L 127.0.0.1:6891:127.0.0.1:6891 -L 127.0.0.1:6892:127.0.0.1:6892 -- node2.lan
node-9-tunnel: wait up to 10 seconds for ports 6890 6891 6892
node-9-tunnel: run just launch-shell 1 --key-type uuid
node-9-tunnel: stop the tunnel after the command exits

Why each part

- CORE_PORT=6890: the shell's RSocket client defaults to 127.0.0.1:6790 (client-rsocket-local.yml). The tunnel gives you node2 host port 6890, which compose maps to container 6790.
- ROOTKEY_SOURCE_URI=http://127.0.0.1:6891: the shell reads root keys from the management actuator at 6791 by default. Through the tunnel that is 6891.
- --key-type uuid: every launch is a deployment and names a key type. There is no default.
- 0 offset: the tunnel needs a multiple of 100, and 0 keeps 6890/6891/6892. With offset 100 use CORE_PORT=6990 and ROOTKEY_SOURCE_URI=http://127.0.0.1:6991.
- non-persistent: the tunnel opens, the shell starts, and the tunnel closes when the shell exits. Standard input stays attached, so the prompt works.

Persistent variant

node-9/bin/node-9-tunnel node2.lan 0 persistent      # prints the tunnel pid
CORE_PORT=6890 ROOTKEY_SOURCE_URI=http://127.0.0.1:6891 just launch-shell 1 --key-type uuid
kill <tunnel-pid>

Build once, after a pull: just build-shell. The launch runs spring-boot:run, so it needs the installed jars. Add --build to the launch to do both.

Login: the interactive prompt signs in with the shell's login command. The environment form (CHAT_SHELL_USER with CHAT_SHELL_PASSWORD_FILE) applies to one-shot and portal modes, and a wrong password gives status 77.
