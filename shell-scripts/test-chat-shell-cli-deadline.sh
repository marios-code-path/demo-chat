#!/usr/bin/env bash
# Checks the hard limit of `register-read` in shell-scripts/chat-shell-cli, from
# the launch of the script. The JVM has its own watchdog, but it cannot stop a
# stall that comes before Java starts. These cases stub chat-build and java, so
# no JVM, no core, and no network run. See CHAT-jmnjvupr and Sigma's review of
# PR #259. Each case has a 20-second alarm, so a missing limit fails and does
# not hang.
set -uo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?

here="$(cd "$(dirname "$0")" && pwd)"
# The harness pins the real perl, because a case puts a stub perl first on the PATH of the launcher.
real_perl="$(command -v perl)"
now_ms() { "$real_perl" -MTime::HiRes=time -e 'printf "%d\n", time * 1000'; }
failures=0

T="$(mktemp -d)"
trap 'for f in "$T"/pid.*; do [[ -f "$f" ]] && kill -KILL "$(cat "$f")" 2>/dev/null; done; rm -rf "$T"' EXIT
mkdir -p "$T/shell-scripts" "$T/chat-deploy/target" "$T/bin"
cp "$here/chat-shell-cli" "$T/shell-scripts/chat-shell-cli"
echo "unused.jar" > "$T/chat-deploy/target/shell-cli.classpath"

# stub_build BODY  The chat-build stub prints a plan with one option after BODY.
stub_build() {
    printf '#!/usr/bin/env bash\n%s\nprintf "  -Dapp.key.type=uuid\\n"\n' "$1" > "$T/shell-scripts/chat-build"
    chmod +x "$T/shell-scripts/chat-build"
}
# stub_java BODY
stub_java() {
    printf '#!/usr/bin/env bash\n%s\n' "$1" > "$T/bin/java"
    chmod +x "$T/bin/java"
}

# run_case NAME EXPECTED-EXIT STAMP-AGE-MS MAX-WALL-MS STDIN-TEXT -- WORDS...
# It leaves stdout in $T/out, standard error in $T/err, and the wall time in $wall.
run_case() {
    local name="$1" expected="$2" age="$3" max="$4" input="$5"
    shift 6
    local start status
    start="$(now_ms)"
    printf '%s' "$input" | PATH="$T/bin:$PATH" CHAT_SHELL_KEY_TYPE=uuid \
        CHAT_SHELL_LAUNCH_EPOCH_MS="$(( start - age ))" \
        "$real_perl" -e 'alarm 20; exec @ARGV' "$T/shell-scripts/chat-shell-cli" "$@" > "$T/out" 2> "$T/err"
    status=$?
    wall=$(( $(now_ms) - start ))
    if [[ "$status" != "$expected" ]]; then
        echo "FAIL $name: exit $status, expected $expected"
        failures=$((failures + 1))
        return 1
    fi
    if (( wall > max )); then
        echo "FAIL $name: took $wall ms, the most allowed is $max ms"
        failures=$((failures + 1))
        return 1
    fi
    return 0
}

ok() { echo "ok   $1"; }
fail() { echo "FAIL $1: $2"; failures=$((failures + 1)); }

LIMIT_LINE='chat-shell: register-read passed its 30000 ms limit'

# gone NAME  The process whose PID a stub wrote to $T/pid.NAME must be dead. A missing PID file is a
# failure, because then the check did not look at any process. The test uses kill -0 on the exact
# PID, so it works where a process list is not readable.
gone() {
    local file="$T/pid.$1" pid i
    [[ -s "$file" ]] || { echo "no PID file for $1"; return 1; }
    pid="$(cat "$file")"
    for i in 1 2 3 4 5 6 7 8 9 10; do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 0.3
    done
    echo "process $pid ($1) is still alive"
    return 1
}

# 1. The launcher stalls before Java: chat-build never answers.
rm -f "$T"/pid.*
stub_build "sleep 59.5 & echo \$! > '$T/pid.build'; wait"
stub_java 'exit 0'
if run_case stall-before-java 124 28000 8000 '' -- register-read; then
    if [[ -s "$T/out" ]]; then fail stall-before-java "standard output is not empty"
    elif [[ "$(cat "$T/err")" != "$LIMIT_LINE" ]]; then fail stall-before-java "standard error is: $(cat "$T/err")"
    elif ! why="$(gone build)"; then fail stall-before-java "$why"
    else ok stall-before-java; fi
fi

# 2. Java stalls after a fast launcher.
rm -f "$T"/pid.*
stub_build ':'
stub_java "sleep 59.5 & echo \$! > '$T/pid.java'; wait"
if run_case stall-in-java 124 28000 8000 '' -- register-read; then
    if [[ -s "$T/out" || "$(cat "$T/err")" != "$LIMIT_LINE" ]]; then fail stall-in-java "output: $(cat "$T/out") error: $(cat "$T/err")"
    elif ! why="$(gone java)"; then fail stall-in-java "$why"
    else ok stall-in-java; fi
fi

# 1b. A child that ignores TERM must still die. Sigma reproduced a survivor, and a second stderr line
# from the job notice of bash, on PR #259.
rm -f "$T"/pid.*
stub_build "trap '' TERM; echo \$\$ > '$T/pid.script'; sleep 59.5 & echo \$! > '$T/pid.sleep'; wait"
stub_java 'exit 0'
if run_case term-resistant-child 124 28000 8000 '' -- register-read; then
    if [[ -s "$T/out" ]]; then fail term-resistant-child "standard output is not empty"
    elif [[ "$(cat "$T/err")" != "$LIMIT_LINE" ]]; then fail term-resistant-child "standard error is: $(cat "$T/err")"
    elif ! why="$(gone script)"; then fail term-resistant-child "$why"
    elif ! why="$(gone sleep)"; then fail term-resistant-child "$why"
    else ok term-resistant-child; fi
fi

# 1c. The launcher needs no perl. A stub perl would sleep, and a stamp 28 s old would leave 2 s.
# The run must end at once with the status of Java, and the stub must never start.
rm -f "$T"/pid.*
cat > "$T/bin/perl" <<STUB
#!/usr/bin/env bash
echo \$\$ > '$T/pid.perl'
exec sleep 59.5
STUB
chmod +x "$T/bin/perl"
stub_build ':'
stub_java 'exit 0'
if run_case no-perl 0 28000 4000 '' -- register-read; then
    if [[ -e "$T/pid.perl" ]]; then fail no-perl "the launcher started perl"
    else ok no-perl; fi
fi
rm -f "$T/bin/perl"

# 1d. A clock that stalls fails closed: with a stamp from the caller, the age is unknown, so the
# launcher ends the command with 124 after 2 s and one line.
rm -f "$T"/pid.*
cat > "$T/bin/date" <<STUB
#!/usr/bin/env bash
echo \$\$ > '$T/pid.date'
exec sleep 59.5
STUB
chmod +x "$T/bin/date"
stub_build ':'
stub_java 'exit 0'
if run_case stalled-clock 124 28000 6000 '' -- register-read; then
    if [[ -s "$T/out" || "$(cat "$T/err")" != "$LIMIT_LINE" ]]; then fail stalled-clock "output: $(cat "$T/out") error: $(cat "$T/err")"
    elif ! why="$(gone date)"; then fail stalled-clock "$why"
    else ok stalled-clock; fi
fi
rm -f "$T/bin/date"

# 2b. Java writes its own limit line and then does not exit in time. The supervisor and the child
# can both fire at the limit, and standard error must still hold one line.
rm -f "$T"/pid.*
stub_build ':'
stub_java "echo 'chat-shell: register-read passed its 30000 ms limit' >&2; sleep 59.5 & echo \$! > '$T/pid.race'; wait"
if run_case one-line-on-race 124 28000 8000 '' -- register-read; then
    if [[ "$(cat "$T/err")" != "$LIMIT_LINE" ]]; then fail one-line-on-race "standard error is: $(cat "$T/err")"
    elif ! why="$(gone race)"; then fail one-line-on-race "$why"
    else ok one-line-on-race; fi
fi

# 3. The budget is spent before the launcher starts any work.
rm -f "$T/ran"
stub_build "touch '$T/ran'"
stub_java "touch '$T/ran'"
if run_case spent-budget 124 31000 3000 '' -- register-read; then
    if [[ -e "$T/ran" ]]; then fail spent-budget "chat-build or java ran"
    elif [[ -s "$T/out" || "$(cat "$T/err")" != "$LIMIT_LINE" ]]; then fail spent-budget "output: $(cat "$T/out") error: $(cat "$T/err")"
    else ok spent-budget; fi
fi

# 4. A fast run keeps its exit status, its standard input, and its standard output.
stub_build ':'
stub_java 'cat; exit 3'
if run_case passes-through 3 1000 8000 'hello input' -- register-read; then
    if [[ "$(cat "$T/out")" != "hello input" ]]; then fail passes-through "stdout is: $(cat "$T/out")"
    elif [[ -s "$T/err" ]]; then fail passes-through "standard error is: $(cat "$T/err")"
    else ok passes-through; fi
fi

# 4b. A run that ends in time passes its standard error through.
stub_build ':'
stub_java 'echo "a warning" >&2; exit 0'
if run_case passes-stderr 0 1000 8000 '' -- register-read; then
    if [[ "$(cat "$T/err")" != "a warning" ]]; then fail passes-stderr "standard error is: $(cat "$T/err")"
    else ok passes-stderr; fi
fi

# 5. Another command has no limit here, even with an old stamp.
stub_build ':'
stub_java 'exit 0'
if run_case no-limit-elsewhere 0 40000 8000 '' -- whoami; then
    if [[ -s "$T/err" ]]; then fail no-limit-elsewhere "standard error is: $(cat "$T/err")"
    else ok no-limit-elsewhere; fi
fi

# 6. No child of a normal run is left behind. The stub starts a sleep and leaves it to the launcher.
rm -f "$T"/pid.*
stub_build ':'
stub_java "sleep 59.5 & echo \$! > '$T/pid.left'; exit 0"
run_case clean-exit 0 1000 8000 '' -- register-read
if ! why="$(gone left)"; then fail clean-exit "$why"; else ok clean-exit; fi

(( failures == 0 )) || { echo "$failures case(s) failed"; exit 1; }
echo "all cases passed"
