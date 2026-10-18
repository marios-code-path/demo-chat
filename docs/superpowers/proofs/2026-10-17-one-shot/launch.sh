#!/usr/bin/env bash
# Starts one chat-shell JVM of the stage 0 proof, inside the isolation sandbox,
# against the disposable core on 6990 and 6991. The JVM options come from the
# chat-build shell plan with the default feature set. They go on the java
# command line, not in JAVA_TOOL_OPTIONS, because the JVM prints a "Picked up
# JAVA_TOOL_OPTIONS" line on standard error for that variable. INTERACTIVE
# (default false) sets spring.shell.interactive.enabled.
set -euo pipefail
S="${1:?usage: launch.sh <scratch-dir> [shell-args...]}"
shift
root="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
if [[ ! -s "$S/shell.opts" ]]; then
    (cd "$root" && CORE_PORT=6990 CORE_MGMT_PORT=6991 ./shell-scripts/chat-build shell \
        --run --notls --node-id 1 --key-type uuid --dry-run) \
        | sed -nE 's/^  +((-D|--)[^ ].*)$/\1/p' \
        | grep -v '^-Dspring-boot\.run\.' > "$S/shell.opts"
fi
opts=()
while IFS= read -r line; do
    [[ "$line" == -Dspring.shell.interactive.enabled=* ]] && continue
    opts+=("$line")
done < "$S/shell.opts"
opts+=("-Dspring.shell.interactive.enabled=${INTERACTIVE:-false}")
exec sandbox-exec -f "$S/isolation.sb" env CORE_PORT=6990 CORE_MGMT_PORT=6991 \
    java "${opts[@]}" -cp "$root/chat-deploy/target/classes:$(cat "$S/shell.cp")" \
    com.demo.chat.ChatAppKt "$@"
