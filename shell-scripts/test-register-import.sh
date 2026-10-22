#!/usr/bin/env bash
# Check that the register split preserves every source byte. See
# CHAT-jmnjvupr. The prototype import-register and its preparation cases are
# removed. shell-scripts/test-register-bundle.sh and
# shell-scripts/test-register-bootstrap.sh hold the import cases now.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
splitter="$repo_root/node-9/bin/register-split"
source_commit=2d75f0cdd09115da422cdc0a693f0227f31a6ef5
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

mode() {
    case "$(uname -s)" in
        Darwin) stat -f '%Lp' "$1" ;;
        *) stat -c '%a' "$1" ;;
    esac
}

check_source() {
    local label="$1" source="$2" expected="$3" out="$work/$1-out"
    local count=0 path hash actual_hash
    "$splitter" "$source" "$out"
    [[ "$(mode "$out")" == 700 ]]
    [[ "$(mode "$out/sections.jsonl")" == 600 ]]
    : > "$work/recovered"
    while IFS= read -r row; do
        path="$(jq -r '.path' <<< "$row")"
        hash="$(jq -r '.sha256' <<< "$row")"
        [[ "$(mode "$out/$path")" == 600 ]]
        actual_hash="$(shasum -a 256 "$out/$path" | cut -d ' ' -f 1)"
        [[ "$hash" == "$actual_hash" ]]
        cat "$out/$path" >> "$work/recovered"
        count=$((count + 1))
    done < "$out/sections.jsonl"
    [[ "$count" -eq "$expected" ]]
    cmp "$source" "$work/recovered"
    printf 'ok %s sections=%s bytes=%s\n' "$label" "$count" "$(wc -c < "$source" | tr -d ' ')"
}

printf 'Preamble\n## First\ntext\n## Second\n' > "$work/lf"
check_source lf "$work/lf" 3

printf 'Preamble\r\n## First\r\ntext\r\n' > "$work/crlf"
check_source crlf "$work/crlf" 2

printf 'Préface\n## Café\nαβγ\n' > "$work/utf8"
check_source utf8 "$work/utf8" 2

printf 'Preamble\n## A\n## B\n' > "$work/empty"
check_source empty "$work/empty" 3

printf 'Preamble\n## Final\nno final newline' > "$work/no-final-lf"
check_source no-final-lf "$work/no-final-lf" 2

printf '## No preamble\n' > "$work/no-preamble"
if "$splitter" "$work/no-preamble" "$work/no-preamble-out" >/dev/null 2>&1; then
    printf 'unexpected success: no preamble\n' >&2
    exit 1
fi

printf 'Preamble\n\0' > "$work/nul"
if "$splitter" "$work/nul" "$work/nul-out" >/dev/null 2>&1; then
    printf 'unexpected success: NUL byte\n' >&2
    exit 1
fi

printf 'Preamble\n\377' > "$work/invalid-utf8"
if "$splitter" "$work/invalid-utf8" "$work/invalid-utf8-out" >/dev/null 2>&1; then
    printf 'unexpected success: invalid UTF-8\n' >&2
    exit 1
fi

if "$splitter" "$work/lf" "$work/lf-out" >/dev/null 2>&1; then
    printf 'unexpected success: existing output directory\n' >&2
    exit 1
fi

total_sections=0
total_bytes=0
for file in \
    forward-register.md \
    forward-register-archive-2026-08.md \
    forward-register-archive-2026-09.md \
    forward-register-archive-2026-10.md \
    forward-register-archive-2026-10-17.md \
    forward-register-archive-2026-10-18.md \
    forward-register-archive-2026-10-19.md; do
    git -C "$repo_root" show "$source_commit:$file" > "$work/$file"
    "$splitter" "$work/$file" "$work/$file-out"
    : > "$work/recovered"
    while IFS= read -r path; do
        cat "$work/$file-out/$path" >> "$work/recovered"
    done < <(jq -r '.path' "$work/$file-out/sections.jsonl")
    cmp "$work/$file" "$work/recovered"
    sections="$(jq -s 'length' "$work/$file-out/sections.jsonl")"
    bytes="$(wc -c < "$work/$file" | tr -d ' ')"
    total_sections=$((total_sections + sections))
    total_bytes=$((total_bytes + bytes))
    printf 'ok %s sections=%s bytes=%s\n' "$file" "$sections" "$bytes"
done
[[ "$total_sections" -eq 117 ]]
[[ "$total_bytes" -eq 317268 ]]
printf 'all register split cases passed: %s sections, %s bytes\n' "$total_sections" "$total_bytes"

