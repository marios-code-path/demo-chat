#!/usr/bin/env bash
# Validate pinned source fixtures without changing a deployment.
set -uo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
checker="$repo/shell-scripts/check-tool-source"
source_dir="$work/source with spaces"
checks=0
failures=0
commit_fixture() {
    git -C "$source_dir" add .
    git -C "$source_dir" -c user.name=Fixture -c user.email=fixture@example.invalid \
        -c commit.gpgsign=false commit -qm 'Record fixture source'
    pin=$(git -C "$source_dir" rev-parse HEAD)
    jq --arg commit "$pin" '. + {demoChatCommit:$commit}' \
        "$source_dir/shell-scripts/tool-files.json" > "$work/lock.json"
}
fixture() {
    rm -rf "$source_dir"
    mkdir -p "$source_dir/shell-scripts"
    git -C "$source_dir" init -q
    printf '1\n' > "$source_dir/shell-scripts/tool-interface.version"
    printf '#!/usr/bin/env bash\nexit 0\n' > "$source_dir/shell-scripts/tool"
    printf 'fixture support\n' > "$source_dir/shell-scripts/support.sh"
    cat > "$source_dir/shell-scripts/tool-files.json" <<'EOF'
{"interfaceVersion":1,"sourceTools":[{"source":"shell-scripts/tool"}],"runtimeFiles":[{"source":"shell-scripts/support.sh","target":"support.sh"}]}
EOF
    printf '__pycache__/\n' > "$source_dir/.gitignore"
    commit_fixture
}
run_check() {
    status=0
    env DEMO_CHAT_HOME="$source_dir" "$@" bash "$checker" "$work/lock.json" \
        > "$work/out" 2> "$work/error" || status=$?
}
check() {
    checks=$((checks + 1))
    if "$@"; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s\n' "$label"
        cat "$work/error" >&2
        failures=$((failures + 1))
    fi
}
refused() { test "$status" -ne 0; }

fixture
index_before=$(shasum -a 256 "$source_dir/.git/index")
run_check env
label='an exact clean source pin passes'
check test "$status" -eq 0
index_after=$(shasum -a 256 "$source_dir/.git/index")
label='the check preserves the Git index'
check test "$index_before" = "$index_after"
label='the check creates no Python cache'
check test ! -d "$source_dir/shell-scripts/__pycache__"
mkdir -p "$source_dir/shell-scripts/__pycache__"
printf 'generated fixture\n' > "$source_dir/shell-scripts/__pycache__/probe.pyc"
run_check env
label='ordinary ignored bytecode preserves source acceptance'
check test "$status" -eq 0

for mutation in no-rule local-rule global-rule negative-rule tracked-change untracked-source hidden-local hidden-global \
    wrong-pin short-pin invalid-pin version missing-support traversal absolute-source duplicate-target \
    target-path malformed-lock unknown-field untracked-rule tracked-bytecode catalogue-mismatch symlink parent-symlink; do
    fixture
    case "$mutation" in
        no-rule) : > "$source_dir/.gitignore"; commit_fixture ;;
        local-rule) : > "$source_dir/.gitignore"; commit_fixture; printf '__pycache__/\n' > "$source_dir/.git/info/exclude" ;;
        global-rule)
            : > "$source_dir/.gitignore"; commit_fixture
            printf '__pycache__/\n' > "$work/global-exclude"
            git -C "$source_dir" config core.excludesFile "$work/global-exclude" ;;
        negative-rule) printf '!__pycache__/\n' > "$source_dir/.gitignore"; commit_fixture ;;
        tracked-change) printf 'changed\n' >> "$source_dir/shell-scripts/tool" ;;
        untracked-source) printf 'untracked\n' > "$source_dir/shell-scripts/extra.sh" ;;
        hidden-local|hidden-global)
            printf 'untracked\n' > "$source_dir/shell-scripts/extra.sh"
            if [[ "$mutation" == hidden-local ]]; then
                printf 'extra.sh\n' > "$source_dir/.git/info/exclude"
            else
                printf 'extra.sh\n' > "$work/global-exclude"
                git -C "$source_dir" config core.excludesFile "$work/global-exclude"
            fi ;;
        wrong-pin) jq '.demoChatCommit="0000000000000000000000000000000000000000"' "$work/lock.json" > "$work/new"; mv "$work/new" "$work/lock.json" ;;
        short-pin) jq '.demoChatCommit="1234567"' "$work/lock.json" > "$work/new"; mv "$work/new" "$work/lock.json" ;;
        invalid-pin) jq '.demoChatCommit="zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"' "$work/lock.json" > "$work/new"; mv "$work/new" "$work/lock.json" ;;
        version) printf '2\n' > "$source_dir/shell-scripts/tool-interface.version"; commit_fixture ;;
        missing-support) rm "$source_dir/shell-scripts/support.sh"; commit_fixture ;;
        traversal)
            jq '.runtimeFiles[0].source="../outside"' "$source_dir/shell-scripts/tool-files.json" > "$work/new"
            mv "$work/new" "$source_dir/shell-scripts/tool-files.json"; commit_fixture ;;
        absolute-source)
            jq '.runtimeFiles[0].source="/absolute"' "$source_dir/shell-scripts/tool-files.json" > "$work/new"
            mv "$work/new" "$source_dir/shell-scripts/tool-files.json"; commit_fixture ;;
        target-path)
            jq '.runtimeFiles[0].target="../escape"' "$source_dir/shell-scripts/tool-files.json" > "$work/new"
            mv "$work/new" "$source_dir/shell-scripts/tool-files.json"; commit_fixture ;;
        malformed-lock) printf 'not JSON\n' > "$work/lock.json" ;;
        unknown-field) jq '.unexpected=true' "$work/lock.json" > "$work/new"; mv "$work/new" "$work/lock.json" ;;
        duplicate-target)
            jq '.runtimeFiles += [.runtimeFiles[0]]' "$source_dir/shell-scripts/tool-files.json" > "$work/new"
            mv "$work/new" "$source_dir/shell-scripts/tool-files.json"; commit_fixture ;;
        untracked-rule)
            git -C "$source_dir" rm -q .gitignore; commit_fixture
            printf '__pycache__/\n' > "$source_dir/.gitignore"
            printf '.gitignore\n' > "$source_dir/.git/info/exclude" ;;
        tracked-bytecode)
            mkdir -p "$source_dir/shell-scripts/__pycache__"
            printf 'tracked\n' > "$source_dir/shell-scripts/__pycache__/old.pyc"
            git -C "$source_dir" add -f shell-scripts/__pycache__/old.pyc; commit_fixture ;;
        catalogue-mismatch)
            jq '.runtimeFiles=[]' "$work/lock.json" > "$work/new"; mv "$work/new" "$work/lock.json" ;;
        symlink)
            rm "$source_dir/shell-scripts/support.sh"
            ln -s "$work/lock.json" "$source_dir/shell-scripts/support.sh"; commit_fixture ;;
        parent-symlink)
            mkdir -p "$work/external"
            printf 'external support\n' > "$work/external/support.sh"
            ln -s "$work/external" "$source_dir/link"
            jq '.runtimeFiles[0].source="link/support.sh"' "$source_dir/shell-scripts/tool-files.json" > "$work/new"
            mv "$work/new" "$source_dir/shell-scripts/tool-files.json"; commit_fixture ;;
    esac
    run_check env
    label="$mutation source is refused"
    check refused
done
fixture
status=0
env -u DEMO_CHAT_HOME bash "$checker" "$work/lock.json" > "$work/out" 2> "$work/error" || status=$?
label='an absent source selector refuses discovery'
check refused
status=0
DEMO_CHAT_HOME=relative bash "$checker" "$work/lock.json" > "$work/out" 2> "$work/error" || status=$?
label='a relative source selector is refused'
check refused
printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
