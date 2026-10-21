#!/usr/bin/env bash
# Check that the register split preserves every source byte.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
splitter="$repo_root/node-9/bin/register-split"
importer="$repo_root/node-9/bin/import-register"
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

printf '#!/usr/bin/env bash\nprintf "called\\n" >> "%s"\n' "$work/shell-called" > "$work/shell-stub"
chmod +x "$work/shell-stub"
printf 'stage4-secret-sentinel\n' > "$work/unused-password"
CHAT_SHELL_CLI="$work/shell-stub" \
    CHAT_SHELL_PASSWORD_FILE="$work/unused-password" \
    "$importer" prepare "$work/state"
[[ ! -e "$work/shell-called" ]]
if rg -a -q 'stage4-secret-sentinel' "$work/state"; then
    printf 'the prepared state holds a secret\n' >&2
    exit 1
fi
[[ "$(mode "$work/state")" == 700 ]]
[[ "$(mode "$work/state/requests.jsonl")" == 600 ]]
[[ "$(jq -s 'length' "$work/state/requests.jsonl")" -eq 131 ]]
[[ "$(jq -r '.request_id' "$work/state/requests.jsonl" | sort -u | wc -l | tr -d ' ')" -eq 131 ]]
[[ "$(jq -r 'select(.kind == "section") | .request_id' "$work/state/requests.jsonl" | wc -l | tr -d ' ')" -eq 117 ]]
[[ "$(jq -r 'select(.kind == "header") | .request_id' "$work/state/requests.jsonl" | wc -l | tr -d ' ')" -eq 7 ]]
[[ "$(jq -r 'select(.kind == "pointer") | .request_id' "$work/state/requests.jsonl" | wc -l | tr -d ' ')" -eq 7 ]]
rg -q "Commit: $source_commit" "$work/state/headers/forward-register.md.txt"
manifest_hash="$(shasum -a 256 "$work/state/requests.jsonl" | cut -d ' ' -f 1)"
CHAT_SHELL_CLI="$work/shell-stub" "$importer" prepare "$work/state"
[[ "$(shasum -a 256 "$work/state/requests.jsonl" | cut -d ' ' -f 1)" == "$manifest_hash" ]]
[[ ! -e "$work/shell-called" ]]

chmod 0755 "$work/state"
if "$importer" prepare "$work/state" >/dev/null 2>&1; then
    printf 'unexpected success: public state directory\n' >&2
    exit 1
fi
chmod 0700 "$work/state"

mkdir "$work/incomplete"
if "$importer" prepare "$work/incomplete" >/dev/null 2>&1; then
    printf 'unexpected success: incomplete state\n' >&2
    exit 1
fi

first_section="$(jq -r 'select(.kind == "section") | .path' "$work/state/requests.jsonl" | head -n 1)"
printf 'changed' >> "$work/state/$first_section"
if "$importer" prepare "$work/state" >/dev/null 2>&1; then
    printf 'unexpected success: changed section\n' >&2
    exit 1
fi
printf 'all register import preparation cases passed\n'
