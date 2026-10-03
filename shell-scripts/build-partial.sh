#!/bin/bash
# Build selected modules and their required project dependencies.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"

usage() {
    printf '%s\n' \
        'Usage: build-partial.sh --modules MODULE[,MODULE...] [--mode unit|integration] [--dry-run]' \
        'Use module directory names from the root pom.xml.' \
        'Unit mode is the default. Both modes reach verify to create required test JARs.' \
        'Integration mode includes container tests. Shell tests also build their image.' \
        'A dry run prints the command without starting Maven.'
}

fail() {
    printf 'build-partial: %s\n' "$1" >&2
    exit 2
}

modules=''
mode='unit'
dry_run=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --modules|--mode)
            [[ $# -ge 2 ]] || fail "Option $1 requires a value."
            case "$1" in
                --modules) modules="$2" ;;
                --mode) mode="$2" ;;
            esac
            shift 2
            ;;
        --dry-run) dry_run=true; shift ;;
        --help|-h) usage; exit 0 ;;
        *) fail "Unknown option: $1" ;;
    esac
done

case "$mode" in
    unit|integration) ;;
    *) fail 'Mode must be unit or integration.' ;;
esac
[[ -n "$modules" ]] || fail 'Specify at least one module with --modules.'
module_pattern='^[a-z][a-z0-9-]*(,[a-z][a-z0-9-]*)*$'
[[ "$modules" =~ $module_pattern ]] || fail 'Use comma-separated module directory names without spaces.'

# The root POM lists one module directory per line.
available="$(sed -n '/<modules>/,/<\/modules>/s/^[[:space:]]*<module>\([^<]*\)<\/module>[[:space:]]*$/\1/p' "$ROOT/pom.xml")"
IFS=',' read -r -a requested <<< "$modules"
selected=''
for module in "${requested[@]}"; do
    if ! printf '%s\n' "$available" | grep -Fxq -- "$module"; then
        fail "Module is absent from the root POM: $module"
    fi
    case ",$selected," in
        *",$module,"*) ;;
        *) selected="${selected:+$selected,}$module" ;;
    esac
done

profiles=''
if [[ "$mode" == integration ]]; then
    profiles='integration'
    case ",$selected," in
        *,chat-shell,*)
            case ",$selected," in
                *,chat-deploy-memory-integration-test,*) ;;
                *) selected+=',chat-deploy-memory-integration-test' ;;
            esac
            ;;
    esac
    case ",$selected," in
        *,chat-deploy-memory-integration-test,*) profiles='test-build,integration' ;;
    esac
fi

# Verify creates test JARs before downstream modules consume them.
# The root POM orders the image module before chat-shell.
command=(mvn -B -pl "$selected" -am clean verify)
if [[ -n "$profiles" ]]; then
    command+=("-P$profiles")
fi

cd "$ROOT"
printf 'Build mode: %s\nSelected modules: %s\n' "$mode" "$selected"
printf 'Command:'
printf ' %q' "${command[@]}"
printf '\n'
if [[ "$dry_run" == true ]]; then
    exit 0
fi
exec "${command[@]}"
