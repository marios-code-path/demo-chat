#!/bin/bash
#
# test-flags.sh — assert chat-build emits the flags recorded under golden/.
#
# chat-build used to be verified by test-parity.sh, which diffed it against
# build-app.sh. Both are gone. This compares chat-build against committed
# expectations instead, so it stays verified without the scripts it replaced.
#
# The goldens were seeded while test-parity.sh still existed and passed every
# case, so they carry the legacy scripts' authority rather than merely freezing
# whatever chat-build happened to emit that day.
#
#   ./test-flags.sh                  # check every case
#   ./test-flags.sh core-memory-tls  # check one case
#   ./test-flags.sh --update         # rewrite goldens, then read the diff
#
# Updating is not a way to make a failure go away. A diff means either a
# deliberate change to the launch contract, in which case commit the new golden
# alongside the change that caused it, or a bug. Decide which before running
# --update.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
GOLDEN="$DIR/golden"
CHAT_BUILD="$DIR/chat-build"

# Pinned so goldens are reproducible on any machine. CONSUL_HOST especially:
# chat-build otherwise shells out to `docker inspect` to find it.
export CONSUL_HOST=10.0.0.5
export KEYSTORE_PASS="golden-test-pass"
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export DEBUG_PORT=5005

# Provenance matters. The four cases marked [parity] were asserted against
# build-app.sh before it was removed, so their goldens carry the legacy scripts'
# authority. The rest are snapshots of chat-build's own output: they detect
# change, but nothing independent ever vouched for their correctness.
#
# Cassandra was never parity-checked and could not have been: run-core.sh built
# the chat-deploy-cassandra module directly, while chat-build builds chat-deploy
# with the cassandra-backend profile, so the two differed by construction rather
# than by mistake.
#
# name | chat-build arguments
CASES=(
  # core backends
  "core-memory-init|core --memory --run --notls --long --init users,rootkeys"          # [parity]
  "core-memory-consul|core --memory --consul --run --notls --long --init users,rootkeys" # [parity]
  "core-memory-tls|core --memory --run --tls /etc/keys --long --init users,rootkeys"   # [parity]
  "core-cassandra|core --cassandra --run --notls --long --init users,rootkeys"
  "core-kafka|core --kafka --run --notls --long"
  "core-redis|core --redis --run --notls --long"
  "core-e2ee|core --memory --e2ee --run --notls --long"
  # core variants
  "core-uuid|core --memory --run --notls --uuid"
  "core-websocket|core --memory --websocket --run --notls --long"
  "core-debug|core --memory --debug --run --notls --long"
  "core-build-image|core --memory --build --notls --long"
  # other services
  "rest-client|rest --run --notls --long"
  "gateway-client|gateway --run --notls --long"
  "authserv-client|authserv --run --notls --long"
  "shell-client|shell --run --notls --long"                                            # [parity]
)

UPDATE=0
ONLY=""
for arg in "$@"; do
    case "$arg" in
        -u|--update) UPDATE=1 ;;
        --help|-h) awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
        -*) echo "unknown option: $arg" >&2; exit 2 ;;
        *) ONLY="$arg" ;;
    esac
done

# One flag per line, quoting stripped, comma-lists de-duplicated, sorted. Same
# normalisation test-parity.sh applies, so goldens seeded from a green parity
# run compare like for like.
normalise() {
  sed "s/'//g" | sed 's/"//g' | python3 -c '
import sys
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    if "=" in line and "," in line.split("=", 1)[1]:
        key, value = line.split("=", 1)
        value = ",".join(dict.fromkeys(value.split(",")))
        line = f"{key}={value}"
    print(line)
' | LC_ALL=C sort -u
}

extract_flags() {
  sed -n '/^# JAVA_TOOL_OPTIONS:/,/^$/p' | sed '1d;/^$/d' | sed 's/^  //' \
    | tr ' ' '\n' | sed '/^$/d'
}

extract_profiles() {
  grep -oE '^mvn .*' | grep -oE '\-P[^ ]+' | sed 's/^-P//' \
    | tr ',' '\n' | sed '/^$/d' | LC_ALL=C sort | paste -sd, -
}

mkdir -p "$GOLDEN"
pass=0
fail=0
updated=0

for entry in "${CASES[@]}"; do
    name="${entry%%|*}"
    args="${entry#*|}"

    [ -n "$ONLY" ] && [ "$ONLY" != "$name" ] && continue

    # shellcheck disable=SC2086
    out="$("$CHAT_BUILD" $args --dry-run 2>&1)"
    if [ $? -ne 0 ]; then
        echo "FAIL  $name — chat-build exited non-zero"
        echo "$out" | sed 's/^/        /'
        fail=$((fail + 1))
        continue
    fi

    actual="$({ echo "# profiles: $(echo "$out" | extract_profiles)"; \
                echo "$out" | extract_flags | normalise; })"
    file="$GOLDEN/$name.flags"

    if [ "$UPDATE" -eq 1 ]; then
        if [ -f "$file" ] && [ "$actual" = "$(cat "$file")" ]; then
            echo "same  $name"
        else
            echo "$actual" > "$file"
            echo "wrote $name"
            updated=$((updated + 1))
        fi
        continue
    fi

    if [ ! -f "$file" ]; then
        echo "FAIL  $name — no golden at ${file#"$DIR"/}; run --update to create it"
        fail=$((fail + 1))
        continue
    fi

    if [ "$actual" = "$(cat "$file")" ]; then
        echo "ok    $name"
        pass=$((pass + 1))
    else
        echo "FAIL  $name — flags differ from golden"
        diff <(cat "$file") <(echo "$actual") | sed 's/^/        /'
        fail=$((fail + 1))
    fi
done

echo
if [ "$UPDATE" -eq 1 ]; then
    echo "goldens: $updated rewritten"
    echo "review the diff before committing — an unexplained change is a bug, not a new baseline"
    exit 0
fi

if [ "$fail" -gt 0 ]; then
    echo "flags: $fail case(s) failed, $pass passed"
    exit 1
fi
echo "flags: all $pass case(s) match"
